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
        when(knowledgeBaseService.search(eq("优化后的需求"), eq(5))).thenReturn(List.of(metadata));

        TestcaseGenerationResult result = service.generate(
                new TestcaseGenerationRequest("验证工作流查询", null));

        assertNotNull(result);
        assertFalse(result.isDegraded());
        assertEquals("public class WorkflowListTest {}", result.getJavaTestCode());
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
        assertEquals("public class WorkflowCreateTest {}", result.getJavaTestCode());
        assertEquals(1, result.getCitations().size());
        assertEquals(TestcaseCitation.TYPE_REFERENCE_URL, result.getCitations().get(0).getType());
        assertEquals(referenceUrl, result.getCitations().get(0).getSource());
        verify(webDocumentCrawler, times(1)).crawl(referenceUrl);
        verify(llmService, times(2)).generateTestCode(anyString());
    }

    @Test
    void generateThrowsWhenKnowledgeBaseMissesAndReferenceUrlAbsent() throws Exception {
        when(llmService.generateTestCode(anyString())).thenReturn("优化后的需求");
        when(knowledgeBaseService.search(eq("优化后的需求"), anyInt())).thenReturn(List.of());

        assertThrows(
                TestcaseReferenceUrlRequiredException.class,
                () -> service.generate(new TestcaseGenerationRequest("验证删除工作流", null)));

        verify(webDocumentCrawler, never()).crawl(anyString());
        verify(llmService, times(1)).generateTestCode(anyString());
    }
}
