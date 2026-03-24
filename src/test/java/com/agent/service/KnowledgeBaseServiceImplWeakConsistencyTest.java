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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
        when(vectorStore.search(any(Embedding.class), eq(3))).thenReturn(List.of(
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
        when(vectorStore.search(any(Embedding.class), eq(4))).thenReturn(List.of(
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
        when(vectorStore.search(any(Embedding.class), eq(5))).thenReturn(List.of(
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

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
