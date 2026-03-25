package com.agent.service.testcase;

import com.agent.crawler.WebDocumentCrawler;
import com.agent.model.ApiMetadata;
import com.agent.service.KnowledgeBaseService;
import com.agent.service.LLMService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestcaseGenerationServiceImplTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private LLMService llmService;

    @Mock
    private WebDocumentCrawler webDocumentCrawler;

    private TestcaseGenerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TestcaseGenerationServiceImpl(
                knowledgeBaseService,
                llmService,
                webDocumentCrawler,
                5);
    }

    @Test
    void generateSucceedsWhenKnowledgeBaseHitExists() throws Exception {
        ApiMetadata metadata = ApiMetadata.builder()
                .apiId("api-list-workflows")
                .className("WorkflowApi")
                .methodName("list")
                .description("list workflows")
                .sourceLocation("https://support.huaweicloud.com/api/list-workflows.html")
                .build();

        when(llmService.generateTestCode(anyString()))
                .thenReturn("优化后的需求")
                .thenReturn("```java\npublic class WorkflowListTest {}\n```");
        when(knowledgeBaseService.search(eq("验证工作流查询"), eq(5))).thenReturn(List.of(metadata));
        when(knowledgeBaseService.search(eq("优化后的需求"), eq(5))).thenReturn(List.of(metadata));

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证工作流查询", null));

        assertNotNull(result);
        assertFalse(result.isDegraded());
        assertTrue(result.getJavaTestCode().contains("public class WorkflowListTest"));
        assertEquals(1, result.getCitations().size());
        assertEquals(TestcaseCitation.TYPE_KNOWLEDGE_BASE, result.getCitations().get(0).getType());
        assertEquals("api-list-workflows", result.getCitations().get(0).getApiId());
        verify(webDocumentCrawler, never()).crawl(anyString());
        verify(llmService, times(2)).generateTestCode(anyString());
    }

    @Test
    void generateSucceedsWhenKnowledgeBaseMissesButReferenceUrlExists() throws IOException {
        String referenceUrl = "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html";
        Metadata metadata = new Metadata();
        metadata.put("source", referenceUrl);
        metadata.put("title", "ModelArts API");
        Document document = Document.from("POST /v1/workflows GET /v1/workflows", metadata);

        when(llmService.generateTestCode(anyString()))
                .thenReturn("优化后的需求")
                .thenReturn("public class WorkflowCreateTest {}");
        when(knowledgeBaseService.search(eq("优化后的需求"), anyInt())).thenReturn(List.of());
        when(webDocumentCrawler.crawl(referenceUrl)).thenReturn(document);

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证创建工作流", referenceUrl));

        assertNotNull(result);
        assertTrue(result.isDegraded());
        assertTrue(result.getJavaTestCode().contains("public class WorkflowCreateTest"));
        assertEquals(1, result.getCitations().size());
        assertEquals(TestcaseCitation.TYPE_REFERENCE_URL, result.getCitations().get(0).getType());
        assertEquals(referenceUrl, result.getCitations().get(0).getSource());
        verify(webDocumentCrawler, times(1)).crawl(referenceUrl);
        verify(llmService, times(2)).generateTestCode(anyString());
    }

    @Test
    void generateThrowsWhenKnowledgeBaseMissesAndReferenceUrlAbsent() throws Exception {
        when(knowledgeBaseService.search(eq("验证删除工作流"), anyInt())).thenReturn(List.of());

        assertThrows(
                TestcaseReferenceUrlRequiredException.class,
                () -> service.generate(new TestcaseGenerationRequest("验证删除工作流", null)));

        verify(webDocumentCrawler, never()).crawl(anyString());
        verify(llmService, never()).generateTestCode(anyString());
    }

    @Test
    void generateFallsBackToRawKnowledgeBaseHitsWhenReferenceUrlAbsent() throws IOException {
        ApiMetadata metadata = ApiMetadata.builder()
                .apiId("api-delete-workflow")
                .className("WorkflowApi")
                .methodName("delete")
                .description("delete workflow")
                .sourceLocation("https://support.huaweicloud.com/api-modelarts/DeleteWorkflow.html")
                .build();

        when(knowledgeBaseService.search(eq("验证删除工作流"), eq(5))).thenReturn(List.of(metadata));
        when(knowledgeBaseService.search(eq("优化后的需求"), eq(5))).thenReturn(List.of());
        when(llmService.generateTestCode(anyString()))
                .thenReturn("优化后的需求")
                .thenReturn("public class DeleteWorkflowTest {}");

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证删除工作流", null));

        assertNotNull(result);
        assertFalse(result.isDegraded());
        assertTrue(result.getJavaTestCode().contains("public class DeleteWorkflowTest"));
        assertEquals(1, result.getCitations().size());
        assertEquals(TestcaseCitation.TYPE_KNOWLEDGE_BASE, result.getCitations().get(0).getType());
        assertEquals("api-delete-workflow", result.getCitations().get(0).getApiId());
        verify(webDocumentCrawler, never()).crawl(anyString());
        verify(llmService, times(2)).generateTestCode(anyString());
    }

    @Test
    void generateNormalizesCredentialPlaceholdersInGeneratedCode() throws IOException {
        String referenceUrl = "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html";
        Metadata metadata = new Metadata();
        metadata.put("source", referenceUrl);
        metadata.put("title", "ModelArts API");
        Document document = Document.from("DELETE /v2/{project_id}/workflows/{workflow_id}", metadata);

        when(llmService.generateTestCode(anyString()))
                .thenReturn("优化后的需求")
                .thenReturn("""
                        import org.junit.jupiter.api.Test;

                        public class DeleteWorkflowTest {
                            private static final String PROJECT_ID = "project_id_placeholder";
                            private static final String AUTH_TOKEN = "auth_token_placeholder";

                            @Test
                            void testDelete() {
                            }
                        }
                        """);
        when(knowledgeBaseService.search(eq("优化后的需求"), anyInt())).thenReturn(List.of());
        when(webDocumentCrawler.crawl(referenceUrl)).thenReturn(document);

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证删除工作流", referenceUrl));

        assertTrue(result.getJavaTestCode().contains("requiredConfig(\"HUAWEICLOUD_PROJECT_ID\", \"hwcloud.project.id\")"));
        assertTrue(result.getJavaTestCode().contains("requiredConfig(\"HUAWEICLOUD_AUTH_TOKEN\", \"hwcloud.auth.token\")"));
        assertFalse(result.getJavaTestCode().toLowerCase().contains("placeholder"));
    }

    @Test
    void generatePassesExpectedFieldsIntoGenerationPrompt() throws IOException {
        String referenceUrl = "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html";
        Metadata metadata = new Metadata();
        metadata.put("source", referenceUrl);
        metadata.put("title", "ModelArts API");
        Document document = Document.from("DELETE /v2/{project_id}/workflows/{workflow_id}", metadata);

        when(llmService.generateTestCode(anyString()))
                .thenReturn("优化后的需求")
                .thenReturn("public class DeleteWorkflowTest {}");
        when(knowledgeBaseService.search(eq("优化后的需求"), anyInt())).thenReturn(List.of());
        when(webDocumentCrawler.crawl(referenceUrl)).thenReturn(document);

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证删除工作流", referenceUrl, 400, "MODELARTS_001", "示例错误描述"));

        assertNotNull(result);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, times(2)).generateTestCode(promptCaptor.capture());
        String generationPrompt = promptCaptor.getAllValues().get(1);
        assertTrue(generationPrompt.contains("expectedHttpStatus: 400"));
        assertTrue(generationPrompt.contains("expectedErrorCode: MODELARTS_001"));
        assertTrue(generationPrompt.contains("expectedErrorDescription: 示例错误描述"));
    }
}
