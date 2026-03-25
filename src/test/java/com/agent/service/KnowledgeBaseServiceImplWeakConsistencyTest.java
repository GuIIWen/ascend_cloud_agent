package com.agent.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.agent.config.KnowledgeBaseConfig;
import com.agent.crawler.WebDocumentCrawler;
import com.agent.model.ApiMetadata;
import com.agent.processor.DocumentProcessor;
import com.agent.storage.MetadataStore;
import com.agent.storage.VectorStoreAdapter;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplWeakConsistencyTest {

    @Mock
    private KnowledgeBaseConfig config;

    @Mock
    private DocumentProcessor documentProcessor;

    @Mock
    private WebDocumentCrawler webCrawler;

    @Mock
    private VectorStoreAdapter vectorStore;

    @Mock
    private MetadataStore metadataStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    void searchFallsBackToSegmentTextWhenApiIdMetadataIsMissing() {
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
                config, documentProcessor, webCrawler, vectorStore, metadataStore, embeddingModel);

        Metadata segmentMetadata = new Metadata();
        segmentMetadata.put("source", "memory://segment");
        TextSegment segment = TextSegment.from("fallback summary", segmentMetadata);

        when(embeddingModel.embed("workflow")).thenReturn(Response.from(Embedding.from(new float[]{0.1f})));
        when(vectorStore.search(any(Embedding.class), eq(20))).thenReturn(List.of(
                new EmbeddingMatch<>(0.98, "match-1", Embedding.from(new float[]{0.1f}), segment)));

        List<ApiMetadata> results = service.search("workflow", 3);

        assertEquals(1, results.size());
        assertEquals("fallback summary", results.getFirst().getDescription());
        assertEquals("memory://segment", results.getFirst().getSourceLocation());
    }

    @Test
    void searchReturnsDegradedHitWhenMetadataRecordIsMissing() throws SQLException {
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
                config, documentProcessor, webCrawler, vectorStore, metadataStore, embeddingModel);
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        Metadata segmentMetadata = new Metadata();
        segmentMetadata.put("apiId", "api-missing");
        segmentMetadata.put("source", "memory://missing");
        TextSegment segment = TextSegment.from("degraded from vector", segmentMetadata);

        when(embeddingModel.embed("workflow")).thenReturn(Response.from(Embedding.from(new float[]{0.1f})));
        when(vectorStore.search(any(Embedding.class), eq(20))).thenReturn(List.of(
                new EmbeddingMatch<>(0.77, "missing", Embedding.from(new float[]{0.1f}), segment)));
        when(metadataStore.findByApiId("api-missing")).thenReturn(null);

        List<ApiMetadata> results = service.search("workflow", 4);

        assertEquals(1, results.size());
        assertEquals("api-missing", results.getFirst().getApiId());
        assertEquals("degraded from vector", results.getFirst().getDescription());
        assertEquals("memory://missing", results.getFirst().getSourceLocation());
        assertTrue(appender.list.stream().anyMatch(event ->
                event.getFormattedMessage().contains("Metadata missing for apiId='api-missing'")));
    }

    @Test
    void searchReturnsDegradedHitWhenMetadataLookupFailsAndLogsError() throws SQLException {
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
                config, documentProcessor, webCrawler, vectorStore, metadataStore, embeddingModel);
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        Metadata goodMetadata = new Metadata();
        goodMetadata.put("apiId", "api-good");
        goodMetadata.put("source", "memory://good");
        Metadata brokenMetadata = new Metadata();
        brokenMetadata.put("apiId", "api-broken");
        brokenMetadata.put("source", "memory://broken");

        when(embeddingModel.embed("workflow")).thenReturn(Response.from(Embedding.from(new float[]{0.1f})));
        when(vectorStore.search(any(Embedding.class), eq(20))).thenReturn(List.of(
                new EmbeddingMatch<>(0.99, "good", Embedding.from(new float[]{0.1f}), TextSegment.from("good", goodMetadata)),
                new EmbeddingMatch<>(0.42, "broken", Embedding.from(new float[]{0.1f}), TextSegment.from("broken", brokenMetadata))));
        when(metadataStore.findByApiId("api-good")).thenReturn(ApiMetadata.builder()
                .apiId("api-good")
                .className("WorkflowApi")
                .methodName("ListWorkflows")
                .description("list workflows")
                .sourceLocation("memory://good")
                .build());
        when(metadataStore.findByApiId("api-broken")).thenThrow(new SQLException("metadata offline"));

        List<ApiMetadata> results = service.search("workflow", 5);

        assertEquals(2, results.size());
        assertEquals("api-good", results.getFirst().getApiId());
        assertEquals("api-broken", results.get(1).getApiId());
        assertEquals("broken", results.get(1).getDescription());
        assertEquals("memory://broken", results.get(1).getSourceLocation());
        assertTrue(appender.list.stream().anyMatch(event ->
                event.getFormattedMessage().contains("Failed to load metadata for apiId='api-broken'")));
    }

    @Test
    void indexJavaProjectContinuesWhenOneFileFails(@TempDir Path tempDir) throws IOException, SQLException {
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
                config, documentProcessor, webCrawler, vectorStore, metadataStore, embeddingModel);

        Path validFile = tempDir.resolve("GoodApi.java");
        Files.writeString(validFile, """
                package demo;

                public class GoodApi {
                    /** fetch workflow */
                    public String loadWorkflow(String id) {
                        return id;
                    }
                }
                """);
        Path anotherValidFile = tempDir.resolve("BrokenApi.java");
        Files.writeString(anotherValidFile, """
                package demo;

                public class BrokenApi {
                    public String broken(String id) {
                        return id;
                    }
                }
                """);

        doNothing()
                .doThrow(new SQLException("metadata write unavailable"))
                .when(metadataStore).save(any(ApiMetadata.class));

        KnowledgeBaseService.IndexStats stats = service.indexJavaProject(tempDir.toString());

        assertEquals(1, stats.getSuccessCount());
        assertEquals(1, stats.getFailureCount());
        assertEquals(2, stats.getTotalDocuments());
        verify(documentProcessor, times(2)).processAndStore(any(Document.class));
    }

    @Test
    void searchDeduplicatesRepeatedApiIds() throws SQLException {
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
                config, documentProcessor, webCrawler, vectorStore, metadataStore, embeddingModel);

        Metadata apiOneMetadata = new Metadata();
        apiOneMetadata.put("apiId", "api-one");
        apiOneMetadata.put("source", "memory://api-one");
        Metadata apiTwoMetadata = new Metadata();
        apiTwoMetadata.put("apiId", "api-two");
        apiTwoMetadata.put("source", "memory://api-two");

        when(embeddingModel.embed("workflow")).thenReturn(Response.from(Embedding.from(new float[]{0.1f})));
        when(vectorStore.search(any(Embedding.class), eq(20))).thenReturn(List.of(
                new EmbeddingMatch<>(0.99, "api-one-1", Embedding.from(new float[]{0.1f}), TextSegment.from("one", apiOneMetadata)),
                new EmbeddingMatch<>(0.98, "api-one-2", Embedding.from(new float[]{0.1f}), TextSegment.from("one again", apiOneMetadata)),
                new EmbeddingMatch<>(0.70, "api-two", Embedding.from(new float[]{0.1f}), TextSegment.from("two", apiTwoMetadata))));
        when(metadataStore.findByApiId("api-one")).thenReturn(apiMetadata(
                "api-one",
                "ListWorkflow",
                "列出工作流",
                "/v1/workflows"));
        when(metadataStore.findByApiId("api-two")).thenReturn(apiMetadata(
                "api-two",
                "DeleteWorkflow",
                "删除工作流",
                "/v1/workflows/{id}"));

        List<ApiMetadata> results = service.search("workflow", 3);

        assertEquals(2, results.size());
        assertEquals("api-one", results.getFirst().getApiId());
        assertEquals("api-two", results.get(1).getApiId());
    }

    @Test
    void searchExpandsSystemDiskQueryToRecoverDetachVolumeApi() throws SQLException {
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
                config, documentProcessor, webCrawler, vectorStore, metadataStore, embeddingModel);

        Metadata cancelObsMetadata = new Metadata();
        cancelObsMetadata.put("apiId", "cancel-obs");
        cancelObsMetadata.put("source", "memory://cancel-obs");
        Metadata detachMetadata = new Metadata();
        detachMetadata.put("apiId", "detach-volume");
        detachMetadata.put("source", "memory://detach-volume");

        when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{0.1f})));
        when(vectorStore.search(any(Embedding.class), eq(20))).thenReturn(
                List.of(new EmbeddingMatch<>(0.99, "cancel-obs", Embedding.from(new float[]{0.1f}), TextSegment.from("cancel obs", cancelObsMetadata))),
                List.of(new EmbeddingMatch<>(0.95, "detach-volume", Embedding.from(new float[]{0.1f}), TextSegment.from("detach volume", detachMetadata))),
                List.of(new EmbeddingMatch<>(0.94, "detach-volume-2", Embedding.from(new float[]{0.1f}), TextSegment.from("detach volume again", detachMetadata))),
                List.of(),
                List.of());
        when(metadataStore.findByApiId("cancel-obs")).thenReturn(apiMetadata(
                "cancel-obs",
                "CancelObs",
                "动态卸载OBS接口",
                "/v1/{project_id}/notebooks/{instance_id}/storage/{storage_id}"));
        when(metadataStore.findByApiId("detach-volume")).thenReturn(apiMetadata(
                "detach-volume",
                "DetachDevServerVolume",
                "Lite Server服务器卸载磁盘接口",
                "/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}"));

        List<ApiMetadata> results = service.search("卸载系统盘", 3);

        assertEquals(2, results.size());
        assertEquals("detach-volume", results.getFirst().getApiId());
        assertEquals("cancel-obs", results.get(1).getApiId());
        verify(vectorStore, atLeast(2)).search(any(Embedding.class), eq(20));
    }

    @Test
    void searchExpandsServerVolumeQueryToPreferDetachVolumeOverDeleteService() throws SQLException {
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
                config, documentProcessor, webCrawler, vectorStore, metadataStore, embeddingModel);

        Metadata deleteServiceMetadata = new Metadata();
        deleteServiceMetadata.put("apiId", "delete-service");
        deleteServiceMetadata.put("source", "memory://delete-service");
        Metadata detachMetadata = new Metadata();
        detachMetadata.put("apiId", "detach-volume");
        detachMetadata.put("source", "memory://detach-volume");
        Metadata deleteDevServerMetadata = new Metadata();
        deleteDevServerMetadata.put("apiId", "delete-dev-server");
        deleteDevServerMetadata.put("source", "memory://delete-dev-server");

        when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{0.1f})));
        when(vectorStore.search(any(Embedding.class), eq(20))).thenReturn(
                List.of(new EmbeddingMatch<>(0.99, "delete-service", Embedding.from(new float[]{0.1f}), TextSegment.from("delete service", deleteServiceMetadata))),
                List.of(new EmbeddingMatch<>(0.96, "delete-dev-server", Embedding.from(new float[]{0.1f}), TextSegment.from("delete dev server", deleteDevServerMetadata))),
                List.of(new EmbeddingMatch<>(0.95, "detach-volume", Embedding.from(new float[]{0.1f}), TextSegment.from("detach volume", detachMetadata))),
                List.of(new EmbeddingMatch<>(0.94, "detach-volume-2", Embedding.from(new float[]{0.1f}), TextSegment.from("detach volume again", detachMetadata))),
                List.of());
        when(metadataStore.findByApiId("delete-service")).thenReturn(apiMetadata(
                "delete-service",
                "DeleteService",
                "删除模型服务",
                "/v1/{project_id}/services/{service_id}"));
        when(metadataStore.findByApiId("delete-dev-server")).thenReturn(apiMetadata(
                "delete-dev-server",
                "DeleteDevServer",
                "删除Lite Server实例接口",
                "/v1/{project_id}/dev-servers/{id}"));
        when(metadataStore.findByApiId("detach-volume")).thenReturn(apiMetadata(
                "detach-volume",
                "DetachDevServerVolume",
                "Lite Server服务器卸载磁盘接口",
                "/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}"));

        List<ApiMetadata> results = service.search("卸载开发服务器卷", 3);

        assertEquals(3, results.size());
        assertEquals("detach-volume", results.getFirst().getApiId());
        assertEquals("delete-dev-server", results.get(1).getApiId());
        assertEquals("delete-service", results.get(2).getApiId());
    }

    private ApiMetadata apiMetadata(String apiId, String methodName, String description, String endpoint) {
        return ApiMetadata.builder()
                .apiId(apiId)
                .className(methodName.toUpperCase())
                .methodName(methodName)
                .description(description)
                .httpMethod("DELETE")
                .endpoint(endpoint)
                .sourceLocation("memory://" + apiId)
                .build();
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
