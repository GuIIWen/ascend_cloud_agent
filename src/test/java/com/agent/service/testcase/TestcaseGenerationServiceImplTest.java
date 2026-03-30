package com.agent.service.testcase;

import com.agent.crawler.WebDocumentCrawler;
import com.agent.model.ApiMetadata;
import com.agent.model.Parameter;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestcaseGenerationServiceImplTest {

    private static final String MINIMAL_JUNIT5_TEST = """
            import org.junit.jupiter.api.Test;

            public class WorkflowListTest {
                @Test
                void testGenerated() {
                }
            }
            """;

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
                .thenReturn("```java\n" + MINIMAL_JUNIT5_TEST + "\n```");
        when(knowledgeBaseService.search(eq("验证工作流查询"), eq(5))).thenReturn(List.of(metadata));
        when(knowledgeBaseService.search(eq("优化后的需求"), eq(5))).thenReturn(List.of(metadata));

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证工作流查询", null));

        assertNotNull(result);
        assertFalse(result.isDegraded());
        assertTrue(result.getJavaTestCode().contains("public class WorkflowListTest"));
        assertEquals("优化后的需求", result.getRefinedRequirement());
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
                .thenReturn("""
                        import org.junit.jupiter.api.Test;

                        public class WorkflowCreateTest {
                            @Test
                            void testGenerated() {
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), anyInt())).thenReturn(List.of());
        when(webDocumentCrawler.crawl(referenceUrl)).thenReturn(document);

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证创建工作流", referenceUrl));

        assertNotNull(result);
        assertTrue(result.isDegraded());
        assertTrue(result.getJavaTestCode().contains("public class WorkflowCreateTest"));
        assertEquals("优化后的需求", result.getRefinedRequirement());
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
                .thenReturn("""
                        import org.junit.jupiter.api.Test;

                        public class DeleteWorkflowTest {
                            @Test
                            void testGenerated() {
                            }
                        }
                        """);

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证删除工作流", null));

        assertNotNull(result);
        assertFalse(result.isDegraded());
        assertTrue(result.getJavaTestCode().contains("public class DeleteWorkflowTest"));
        assertEquals("优化后的需求", result.getRefinedRequirement());
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
        when(knowledgeBaseService.search(anyString(), anyInt())).thenReturn(List.of());
        when(webDocumentCrawler.crawl(referenceUrl)).thenReturn(document);

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证删除工作流", referenceUrl));

        assertEquals("优化后的需求", result.getRefinedRequirement());
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
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DeleteWorkflowTest {
                            @Test
                            void testGenerated() {
                                HttpResponse<String> response = null;
                                String errorCode = "MODELARTS_001";
                                String errorDescription = "示例错误描述";
                                assertEquals(400, response.statusCode());
                                assertEquals("MODELARTS_001", errorCode);
                                assertEquals("示例错误描述", errorDescription);
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), anyInt())).thenReturn(List.of());
        when(webDocumentCrawler.crawl(referenceUrl)).thenReturn(document);

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证删除工作流", referenceUrl, 400, "MODELARTS_001", "示例错误描述"));

        assertNotNull(result);
        assertTrue(result.getRefinedRequirement().contains("前置条件："));
        assertTrue(result.getRefinedRequirement().contains("输入："));
        assertTrue(result.getRefinedRequirement().contains("步骤：调用目标接口。"));
        assertTrue(result.getRefinedRequirement().contains("HTTP状态码=400"));
        assertTrue(result.getRefinedRequirement().contains("错误码=MODELARTS_001"));
        assertTrue(result.getRefinedRequirement().contains("错误描述包含\"示例错误描述\""));
        assertFalse(result.getRefinedRequirement().contains("参数解释"));
        assertFalse(result.getRefinedRequirement().contains("约束限制"));
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, times(2)).generateTestCode(promptCaptor.capture());
        String refinementPrompt = promptCaptor.getAllValues().get(0);
        String generationPrompt = promptCaptor.getAllValues().get(1);
        assertTrue(refinementPrompt.contains("expectedHttpStatus: 400"));
        assertTrue(refinementPrompt.contains("expectedErrorCode: MODELARTS_001"));
        assertTrue(refinementPrompt.contains("expectedErrorDescription: 示例错误描述"));
        assertTrue(refinementPrompt.contains("输出严格使用以下 4 行格式"));
        assertTrue(generationPrompt.contains("expectedHttpStatus: 400"));
        assertTrue(generationPrompt.contains("expectedErrorCode: MODELARTS_001"));
        assertTrue(generationPrompt.contains("expectedErrorDescription: 示例错误描述"));
    }

    @Test
    void generateRetriesWhenFirstGeneratedCodeFailsValidation() throws IOException {
        String referenceUrl = "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html";
        Metadata metadata = new Metadata();
        metadata.put("source", referenceUrl);
        metadata.put("title", "ModelArts API");
        Document document = Document.from("DELETE /v2/{project_id}/workflows/{workflow_id}", metadata);

        when(llmService.generateTestCode(anyString()))
                .thenReturn("优化后的需求")
                .thenReturn("""
                        class InvalidTest {
                            void testGenerated() {
                            }
                        }
                        """)
                .thenReturn("""
                        import org.junit.jupiter.api.Test;

                        public class DeleteWorkflowTest {
                            @Test
                            void testGenerated() {
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), anyInt())).thenReturn(List.of());
        when(webDocumentCrawler.crawl(referenceUrl)).thenReturn(document);

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证删除工作流", referenceUrl));

        assertTrue(result.getJavaTestCode().contains("public class DeleteWorkflowTest"));
        verify(llmService, times(3)).generateTestCode(anyString());
    }

    @Test
    void generateRetriesWhenFirstAttemptMissesTestMethodAndSecondAttemptUsesShortJunitAnnotations() throws IOException {
        String referenceUrl = "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html";
        Metadata metadata = new Metadata();
        metadata.put("source", referenceUrl);
        metadata.put("title", "ModelArts API");
        Document document = Document.from("DELETE /v2/{project_id}/workflows/{workflow_id}", metadata);

        when(llmService.generateTestCode(anyString()))
                .thenReturn("优化后的需求")
                .thenReturn("""
                        public class DetachDevServerVolumeTest {
                            private static String authToken;

                            @BeforeAll
                            static void loadRuntimeConfig() {
                                authToken = requiredConfig("HUAWEICLOUD_AUTH_TOKEN", "hwcloud.auth.token");
                            }

                            private static String requiredConfig(String envKey, String propertyKey) {
                                return "";
                            }
                        }
                        """)
                .thenReturn("""
                        public class DetachDevServerVolumeTest {
                            private static String authToken;

                            @BeforeAll
                            static void loadRuntimeConfig() {
                                authToken = requiredConfig("HUAWEICLOUD_AUTH_TOKEN", "hwcloud.auth.token");
                            }

                            @Test
                            void detachVolume() {
                            }

                            private static String requiredConfig(String envKey, String propertyKey) {
                                return "";
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), anyInt())).thenReturn(List.of());
        when(webDocumentCrawler.crawl(referenceUrl)).thenReturn(document);

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证卸载 Lite Server 系统盘", referenceUrl));

        assertTrue(result.getJavaTestCode().contains("import org.junit.jupiter.api.BeforeAll;"));
        assertTrue(result.getJavaTestCode().contains("import org.junit.jupiter.api.Test;"));
        assertTrue(result.getJavaTestCode().contains("@Test"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, times(3)).generateTestCode(promptCaptor.capture());
        String retryPrompt = promptCaptor.getAllValues().get(2);
        assertTrue(retryPrompt.contains("must be a JUnit5 test class"));
        assertTrue(retryPrompt.contains("至少保留一个 @Test 方法"));
    }

    @Test
    void generateRetriesWhenRealHttpCallMissesTimeout() throws IOException {
        String referenceUrl = "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html";
        Metadata metadata = new Metadata();
        metadata.put("source", referenceUrl);
        metadata.put("title", "ModelArts API");
        Document document = Document.from("DELETE /v2/{project_id}/workflows/{workflow_id}", metadata);

        when(llmService.generateTestCode(anyString()))
                .thenReturn("优化后的需求")
                .thenReturn("""
                        import java.net.URI;
                        import java.net.http.HttpClient;
                        import java.net.http.HttpRequest;
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DeleteWorkflowTest {
                            @Test
                            void testGenerated() throws Exception {
                                HttpClient client = HttpClient.newHttpClient();
                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create("https://example.com"))
                                        .DELETE()
                                        .build();
                                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                                assertNotNull(response);
                            }
                        }
                        """)
                .thenReturn("""
                        import java.net.URI;
                        import java.net.http.HttpClient;
                        import java.net.http.HttpRequest;
                        import java.net.http.HttpResponse;
                        import java.time.Duration;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DeleteWorkflowTest {
                            @Test
                            void testGenerated() throws Exception {
                                HttpClient client = HttpClient.newBuilder()
                                        .connectTimeout(Duration.ofSeconds(10))
                                        .build();
                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create("https://example.com"))
                                        .timeout(Duration.ofSeconds(30))
                                        .DELETE()
                                        .build();
                                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                                assertNotNull(response);
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), anyInt())).thenReturn(List.of());
        when(webDocumentCrawler.crawl(referenceUrl)).thenReturn(document);

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证删除工作流", referenceUrl));

        assertTrue(result.getJavaTestCode().contains("connectTimeout(Duration.ofSeconds(10))"));
        assertTrue(result.getJavaTestCode().contains("timeout(Duration.ofSeconds(30))"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, times(3)).generateTestCode(promptCaptor.capture());
        String retryPrompt = promptCaptor.getAllValues().get(2);
        assertTrue(retryPrompt.contains("must configure HTTP timeout"));
    }

    @Test
    void generateRetriesWhenExplicitErrorAssertionUsesWholeBodyContains() throws IOException {
        String referenceUrl = "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html";
        Metadata metadata = new Metadata();
        metadata.put("source", referenceUrl);
        metadata.put("title", "ModelArts API");
        Document document = Document.from("DELETE /v2/{project_id}/workflows/{workflow_id}", metadata);

        when(llmService.generateTestCode(anyString()))
                .thenReturn("优化后的需求")
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DeleteWorkflowTest {
                            @Test
                            void testGenerated() {
                                HttpResponse<String> response = null;
                                String body = response.body();
                                assertEquals(400, response.statusCode());
                                assertTrue(body.contains("MODELARTS_001"));
                                assertTrue(body.contains("示例错误描述"));
                            }
                        }
                        """)
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DeleteWorkflowTest {
                            @Test
                            void testGenerated() {
                                HttpResponse<String> response = null;
                                String errorCode = "MODELARTS_001";
                                String errorDescription = "示例错误描述";
                                assertEquals(400, response.statusCode());
                                assertEquals("MODELARTS_001", errorCode);
                                assertEquals("示例错误描述", errorDescription);
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), anyInt())).thenReturn(List.of());
        when(webDocumentCrawler.crawl(referenceUrl)).thenReturn(document);

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证删除工作流", referenceUrl, 400, "MODELARTS_001", "示例错误描述"));

        assertFalse(result.getJavaTestCode().contains("body.contains("));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, times(3)).generateTestCode(promptCaptor.capture());
        String retryPrompt = promptCaptor.getAllValues().get(2);
        assertTrue(retryPrompt.contains("must not assert explicit error code/description via whole-body contains"));
        assertTrue(retryPrompt.contains("parse errorCode/errorDescription into variables"));
    }

    @Test
    void generateRetryPromptGuidesNegativeCaseFromWholeBodyContainsToExplicitStatusAssertion() throws IOException {
        String referenceUrl = "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html";
        Metadata metadata = new Metadata();
        metadata.put("source", referenceUrl);
        metadata.put("title", "ModelArts API");
        Document document = Document.from("DELETE /v2/{project_id}/workflows/{workflow_id}", metadata);

        when(llmService.generateTestCode(anyString()))
                .thenReturn("优化后的需求")
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DeleteWorkflowTest {
                            @Test
                            void testGenerated() {
                                HttpResponse<String> response = null;
                                String body = response.body();
                                assertEquals(400, response.statusCode());
                                assertTrue(body.contains("MODELARTS_001"));
                                assertTrue(body.contains("示例错误描述"));
                            }
                        }
                        """)
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DeleteWorkflowTest {
                            @Test
                            void testGenerated() {
                                HttpResponse<String> response = null;
                                String errorCode = "MODELARTS_001";
                                String errorDescription = "示例错误描述";
                                assertEquals("MODELARTS_001", errorCode);
                                assertEquals("示例错误描述", errorDescription);
                            }
                        }
                        """)
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DeleteWorkflowTest {
                            @Test
                            void testGenerated() {
                                HttpResponse<String> response = null;
                                String errorCode = "MODELARTS_001";
                                String errorDescription = "示例错误描述";
                                assertEquals(400, response.statusCode());
                                assertEquals("MODELARTS_001", errorCode);
                                assertEquals("示例错误描述", errorDescription);
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), anyInt())).thenReturn(List.of());
        when(webDocumentCrawler.crawl(referenceUrl)).thenReturn(document);

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证删除工作流", referenceUrl, 400, "MODELARTS_001", "示例错误描述"));

        assertTrue(result.getJavaTestCode().contains("assertEquals(400, response.statusCode())"));
        assertTrue(result.getJavaTestCode().contains("String errorCode = \"MODELARTS_001\";"));
        assertTrue(result.getJavaTestCode().contains("String errorDescription = \"示例错误描述\";"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, times(4)).generateTestCode(promptCaptor.capture());
        String retryPromptAfterWholeBodyContains = promptCaptor.getAllValues().get(2);
        String retryPromptAfterMissingStatus = promptCaptor.getAllValues().get(3);
        assertTrue(retryPromptAfterWholeBodyContains.contains("assertEquals(400, response.statusCode())"));
        assertTrue(retryPromptAfterWholeBodyContains.contains("parse errorCode/errorDescription into variables"));
        assertTrue(retryPromptAfterMissingStatus.contains("assertEquals(400, response.statusCode())"));
        assertTrue(retryPromptAfterMissingStatus.contains("must assert HTTP status 400"));
    }

    @Test
    void generateUsesOnlyTopKnowledgeBaseHitForContextAndCitations() {
        ApiMetadata topHit = ApiMetadata.builder()
                .apiId("api-detach-volume")
                .className("DevServerApi")
                .methodName("detachVolume")
                .description("detach volume")
                .httpMethod("DELETE")
                .endpoint("/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}")
                .parameters(List.of(
                        new Parameter("project_id", "String", "项目ID", true),
                        new Parameter("id", "String", "Lite Server实例ID", true),
                        new Parameter("volume_id", "String", "待卸载磁盘ID", true)))
                .sourceLocation("https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html")
                .build();
        ApiMetadata noisyHit = ApiMetadata.builder()
                .apiId("api-delete-dev-server")
                .className("DevServerApi")
                .methodName("delete")
                .description("delete dev server")
                .httpMethod("DELETE")
                .endpoint("/v1/{project_id}/dev-servers/{id}")
                .sourceLocation("https://support.huaweicloud.com/api-modelarts/DeleteDevServer.html")
                .build();

        when(llmService.generateTestCode(anyString()))
                .thenReturn("优化后的需求")
                .thenReturn("""
                        import org.junit.jupiter.api.Test;

                        public class DetachVolumeTest {
                            @Test
                            void testGenerated() {
                            }
                        }
                        """);
        when(knowledgeBaseService.search(eq("卸载Lite Server系统盘"), eq(5))).thenReturn(List.of(topHit));
        when(knowledgeBaseService.search(argThat(query -> query != null && query.contains("前置条件：")), eq(5)))
                .thenReturn(List.of(topHit, noisyHit));

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("卸载Lite Server系统盘", null));

        assertEquals(1, result.getCitations().size());
        assertEquals("api-detach-volume", result.getCitations().get(0).getApiId());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, times(2)).generateTestCode(promptCaptor.capture());
        String refinementPrompt = promptCaptor.getAllValues().get(0);
        String generationPrompt = promptCaptor.getAllValues().get(1);
        assertTrue(refinementPrompt.contains("候选API锚点"));
        assertTrue(refinementPrompt.contains("/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}"));
        assertTrue(refinementPrompt.contains("parameterNames: project_id、id、volume_id"));
        assertFalse(refinementPrompt.contains("detach volume"));
        assertTrue(generationPrompt.contains("allowedApiId: api-detach-volume"));
        assertFalse(generationPrompt.contains("allowedApiId: api-delete-dev-server"));
        assertTrue(generationPrompt.contains("parameters:"));
        assertTrue(generationPrompt.contains("name=volume_id, type=String, required=true, description=待卸载磁盘ID"));
        assertTrue(generationPrompt.contains("pathParamBinding: id -> DEV_SERVER_ID"));
        assertTrue(generationPrompt.contains("guardrail: use only this allowed API identity"));
        assertTrue(generationPrompt.contains("guardrail: if explicit expectedHttpStatus/expectedErrorCode/expectedErrorDescription are not provided elsewhere in the prompt and not present below, do not fabricate them"));
    }

    @Test
    void generateAlignsRefinedRequirementToTopHitAndExplicitTruth() {
        ApiMetadata topHit = ApiMetadata.builder()
                .apiId("api-detach-volume")
                .description("detach volume")
                .httpMethod("DELETE")
                .endpoint("/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}")
                .parameters(List.of(
                        new Parameter("project_id", "String", "项目ID", true),
                        new Parameter("id", "String", "Lite Server实例ID", true),
                        new Parameter("volume_id", "String", "待卸载磁盘ID", true)))
                .sourceLocation("https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html")
                .build();

        when(llmService.generateTestCode(anyString()))
                .thenReturn("前置条件：BMS实例。断言：错误码=InvalidOperation.SystemDiskDetachNotSupported")
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DetachVolumeTest {
                            @Test
                            void testGenerated() {
                                HttpResponse<String> response = null;
                                String errorCode = "ModelArts.7000";
                                String errorDescription = "does not support detach volume device";
                                assertEquals(400, response.statusCode());
                                assertEquals("ModelArts.7000", errorCode);
                                assertEquals("does not support detach volume device", errorDescription);
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), eq(5))).thenReturn(List.of(topHit));

        TestcaseGenerationResult result = service.generate(new TestcaseGenerationRequest(
                "验证卸载 Lite Server 系统盘在 BMS 场景下返回 400",
                null,
                400,
                "ModelArts.7000",
                "does not support detach volume device"));

        assertTrue(result.getRefinedRequirement().contains("前置条件：BMS实例。"));
        assertTrue(result.getRefinedRequirement().contains("输入：project_id、id、volume_id"));
        assertTrue(result.getRefinedRequirement().contains("步骤：调用 DELETE /v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id} 接口。"));
        assertTrue(result.getRefinedRequirement().contains("断言：HTTP状态码=400，错误码=ModelArts.7000，错误描述包含\"does not support detach volume device\""));
        assertFalse(result.getRefinedRequirement().contains("InvalidOperation.SystemDiskDetachNotSupported"));
    }

    @Test
    void generateExtractsExpectationFromRequirementWhenStructuredFieldsAreAbsent() {
        ApiMetadata topHit = ApiMetadata.builder()
                .apiId("api-detach-volume")
                .description("detach volume")
                .httpMethod("DELETE")
                .endpoint("/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}")
                .parameters(List.of(
                        new Parameter("project_id", "String", "项目ID", true),
                        new Parameter("id", "String", "Lite Server实例ID", true),
                        new Parameter("volume_id", "String", "待卸载磁盘ID", true)))
                .sourceLocation("https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html")
                .build();

        when(llmService.generateTestCode(anyString()))
                .thenReturn("前置条件：BMS实例。断言：待确认")
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DetachVolumeTest {
                            @Test
                            void detach() {
                                HttpResponse<String> response = null;
                                String errorCode = "ModelArts.7000";
                                String errorDescription = "does not support detach volume device";
                                assertEquals(400, response.statusCode());
                                assertEquals("ModelArts.7000", errorCode);
                                assertEquals("does not support detach volume device", errorDescription);
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), eq(5))).thenReturn(List.of(topHit));

        TestcaseGenerationResult result = service.generate(new TestcaseGenerationRequest(
                "验证卸载 Lite Server 系统盘返回400，错误码=ModelArts.7000，错误描述包含\"does not support detach volume device\"",
                null));

        assertTrue(result.getRefinedRequirement().contains("断言：HTTP状态码=400，错误码=ModelArts.7000，错误描述包含\"does not support detach volume device\""));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, times(2)).generateTestCode(promptCaptor.capture());
        String refinementPrompt = promptCaptor.getAllValues().get(0);
        String generationPrompt = promptCaptor.getAllValues().get(1);
        assertTrue(refinementPrompt.contains("expectedHttpStatus: 400"));
        assertTrue(refinementPrompt.contains("expectedErrorCode: ModelArts.7000"));
        assertTrue(refinementPrompt.contains("expectedErrorDescription: does not support detach volume device"));
        assertTrue(generationPrompt.contains("expectedHttpStatus: 400"));
        assertTrue(generationPrompt.contains("expectedErrorCode: ModelArts.7000"));
        assertTrue(generationPrompt.contains("expectedErrorDescription: does not support detach volume device"));
    }

    @Test
    void generateAlignsRefinedRequirementWithoutExplicitTruthToPendingAssertion() {
        ApiMetadata topHit = ApiMetadata.builder()
                .apiId("api-detach-volume")
                .description("detach volume")
                .httpMethod("DELETE")
                .endpoint("/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}")
                .parameters(List.of(
                        new Parameter("project_id", "String", "项目ID", true),
                        new Parameter("id", "String", "Lite Server实例ID", true),
                        new Parameter("volume_id", "String", "待卸载磁盘ID", true)))
                .sourceLocation("https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html")
                .build();

        when(llmService.generateTestCode(anyString()))
                .thenReturn("前置条件：已存在挂载了系统盘的 Lite Server 实例\n断言：系统盘成功卸载")
                .thenReturn("""
                        import org.junit.jupiter.api.Test;

                        public class DetachVolumeTest {
                            @Test
                            void testGenerated() {
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), eq(5))).thenReturn(List.of(topHit));

        TestcaseGenerationResult result = service.generate(new TestcaseGenerationRequest(
                "验证卸载 Lite Server 系统盘",
                null));

        assertTrue(result.getRefinedRequirement().contains("前置条件：已存在挂载了系统盘的 Lite Server 实例"));
        assertTrue(result.getRefinedRequirement().contains("输入：project_id、id、volume_id"));
        assertTrue(result.getRefinedRequirement().contains("步骤：调用 DELETE /v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id} 接口。"));
        assertTrue(result.getRefinedRequirement().contains("断言：待确认"));
        assertFalse(result.getRefinedRequirement().contains("系统盘成功卸载"));
    }

    @Test
    void generateRetriesWhenRequirementDerivedNegativeTruthStillAssertsSuccessOperationFields() {
        ApiMetadata topHit = ApiMetadata.builder()
                .apiId("api-detach-volume")
                .description("detach volume")
                .httpMethod("DELETE")
                .endpoint("/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}")
                .parameters(List.of(
                        new Parameter("project_id", "String", "项目ID", true),
                        new Parameter("id", "String", "Lite Server实例ID", true),
                        new Parameter("volume_id", "String", "待卸载磁盘ID", true)))
                .responseBody("""
                        {
                          "operation_id": "UUID",
                          "operation_status": "running",
                          "operation_type": "node_detach_volume"
                        }
                        """)
                .sourceLocation("https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html")
                .build();

        when(llmService.generateTestCode(anyString()))
                .thenReturn("前置条件：已存在挂载了系统盘的 Lite Server 实例\n断言：待确认")
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DetachVolumeTest {
                            @Test
                            void detach() {
                                HttpResponse<String> response = null;
                                assertEquals(400, response.statusCode());
                                assertTrue(response.body().contains("operation_id"));
                            }
                        }
                        """)
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DetachVolumeTest {
                            @Test
                            void detach() {
                                HttpResponse<String> response = null;
                                assertEquals(400, response.statusCode());
                                assertNotNull(response.body());
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), eq(5))).thenReturn(List.of(topHit));

        TestcaseGenerationResult result = service.generate(new TestcaseGenerationRequest(
                "验证卸载 Lite Server 系统盘在 BMS 场景下返回 400",
                null));

        assertFalse(result.getJavaTestCode().contains("operation_id"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, times(3)).generateTestCode(promptCaptor.capture());
        String retryPrompt = promptCaptor.getAllValues().get(2);
        assertTrue(retryPrompt.contains("must not assert success response fields"));
    }

    @Test
    void generateRetriesWhenNoTruthCodeAssertsResponseBodyFieldsEvenIfKnowledgeBaseHasResponseBody() {
        ApiMetadata topHit = ApiMetadata.builder()
                .apiId("api-detach-volume")
                .description("detach volume")
                .httpMethod("DELETE")
                .endpoint("/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}")
                .parameters(List.of(
                        new Parameter("project_id", "String", "项目ID", true),
                        new Parameter("id", "String", "Lite Server实例ID", true),
                        new Parameter("volume_id", "String", "待卸载磁盘ID", true)))
                .responseBody("""
                        {
                          "operation_id": "UUID",
                          "operation_status": "running"
                        }
                        """)
                .sourceLocation("https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html")
                .build();

        when(llmService.generateTestCode(anyString()))
                .thenReturn("前置条件：已存在挂载了系统盘的 Lite Server 实例\n断言：待确认")
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DetachVolumeTest {
                            @Test
                            void detach() {
                                String devServerId = requiredConfig("HUAWEICLOUD_DEV_SERVER_ID", "hwcloud.dev-server.id");
                                HttpResponse<String> response = null;
                                assertNotNull(devServerId);
                                assertTrue(response.body().contains("operation_id"));
                            }

                            private static String requiredConfig(String envKey, String propertyKey) {
                                return "";
                            }
                        }
                        """)
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DetachVolumeTest {
                            @Test
                            void detach() {
                                String devServerId = requiredConfig("HUAWEICLOUD_DEV_SERVER_ID", "hwcloud.dev-server.id");
                                HttpResponse<String> response = null;
                                assertNotNull(devServerId);
                                assertNotNull(response.body());
                            }

                            private static String requiredConfig(String envKey, String propertyKey) {
                                return "";
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), eq(5))).thenReturn(List.of(topHit));

        TestcaseGenerationResult result = service.generate(new TestcaseGenerationRequest(
                "验证卸载 Lite Server 系统盘",
                null));

        assertFalse(result.getJavaTestCode().contains("operation_id"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, times(3)).generateTestCode(promptCaptor.capture());
        String retryPrompt = promptCaptor.getAllValues().get(2);
        assertTrue(retryPrompt.contains("must not assert response body fields"));
    }

    @Test
    void generateRetriesWhenNoTruthCodeFabricatesAssertionsOrUsesWrongBinding() {
        ApiMetadata topHit = ApiMetadata.builder()
                .apiId("api-detach-volume")
                .description("detach volume")
                .httpMethod("DELETE")
                .endpoint("/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}")
                .parameters(List.of(
                        new Parameter("project_id", "String", "项目ID", true),
                        new Parameter("id", "String", "Lite Server实例ID", true),
                        new Parameter("volume_id", "String", "待卸载磁盘ID", true)))
                .sourceLocation("https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html")
                .build();

        when(llmService.generateTestCode(anyString()))
                .thenReturn("前置条件：已存在挂载了系统盘的 Lite Server 实例\n断言：待确认")
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DetachVolumeTest {
                            @Test
                            void detach() {
                                String serverId = requiredConfig("HUAWEICLOUD_SERVER_ID", "hwcloud.server.id");
                                HttpResponse<String> response = null;
                                assertEquals(200, response.statusCode());
                                assertTrue(response.body().contains("operation_id"));
                            }

                            private static String requiredConfig(String envKey, String propertyKey) {
                                return "";
                            }
                        }
                        """)
                .thenReturn("""
                        import java.net.http.HttpResponse;
                        import org.junit.jupiter.api.Test;
                        import static org.junit.jupiter.api.Assertions.*;

                        public class DetachVolumeTest {
                            @Test
                            void detach() {
                                String devServerId = requiredConfig("HUAWEICLOUD_DEV_SERVER_ID", "hwcloud.dev-server.id");
                                HttpResponse<String> response = null;
                                assertNotNull(devServerId);
                                assertNotNull(response);
                            }

                            private static String requiredConfig(String envKey, String propertyKey) {
                                return "";
                            }
                        }
                        """);
        when(knowledgeBaseService.search(anyString(), eq(5))).thenReturn(List.of(topHit));

        TestcaseGenerationResult result = service.generate(new TestcaseGenerationRequest(
                "验证卸载 Lite Server 系统盘",
                null));

        assertTrue(result.getJavaTestCode().contains("requiredConfig(\"HUAWEICLOUD_DEV_SERVER_ID\", \"hwcloud.dev-server.id\")"));
        assertFalse(result.getJavaTestCode().contains("HUAWEICLOUD_SERVER_ID"));
        assertFalse(result.getJavaTestCode().contains("assertEquals(200"));
        assertFalse(result.getJavaTestCode().contains("operation_id"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, times(3)).generateTestCode(promptCaptor.capture());
        String retryPrompt = promptCaptor.getAllValues().get(2);
        assertTrue(retryPrompt.contains("must use DEV_SERVER_ID binding"));
    }
}
