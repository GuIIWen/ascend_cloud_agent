package com.agent.controller;

import com.agent.model.ApiMetadata;
import com.agent.model.error.ApiErrorResponse;
import com.agent.service.HuaweiCloudApiCrawlerService;
import com.agent.service.KnowledgeBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseControllerTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private HuaweiCloudApiCrawlerService huaweiCloudApiCrawlerService;

    private KnowledgeBaseController controller;

    @BeforeEach
    void setUp() {
        controller = new KnowledgeBaseController(knowledgeBaseService, huaweiCloudApiCrawlerService);
    }

    @Test
    void searchRejectsMissingQueryWithStructuredErrorBody() {
        KnowledgeBaseController.SearchRequest request = new KnowledgeBaseController.SearchRequest();

        Object response = controller.search(request);

        ApiErrorResponse error = assertInstanceOf(ApiErrorResponse.class, response);
        assertEquals("INVALID_ARGUMENT", error.getError().getCode());
        assertEquals("query", error.getError().getDetails().get("field"));
        verifyNoInteractions(knowledgeBaseService);
    }

    @Test
    void searchRejectsInvalidTopKWithStructuredErrorBody() {
        KnowledgeBaseController.SearchRequest request = new KnowledgeBaseController.SearchRequest();
        request.setQuery("  auth login  ");
        request.setTopK(0);

        Object response = controller.search(request);

        ApiErrorResponse error = assertInstanceOf(ApiErrorResponse.class, response);
        assertEquals("topK", error.getError().getDetails().get("field"));
        verifyNoInteractions(knowledgeBaseService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchSuccessKeepsCompatibleFields() {
        KnowledgeBaseController.SearchRequest request = new KnowledgeBaseController.SearchRequest();
        request.setQuery("  auth login  ");
        request.setTopK(3);
        ApiMetadata metadata = ApiMetadata.builder().apiId("api-1").build();
        when(knowledgeBaseService.search(eq("auth login"), eq(3))).thenReturn(List.of(metadata));

        Object response = controller.search(request);

        Map<String, Object> body = assertInstanceOf(Map.class, response);
        assertEquals("auth login", body.get("query"));
        assertEquals(1, body.get("total"));
        assertTrue(body.containsKey("results"));
    }

    @Test
    void indexRejectsBlankUrl() {
        KnowledgeBaseController.IndexRequest request = new KnowledgeBaseController.IndexRequest();
        request.setUrl(" ");

        Object response = controller.index(request);

        ApiErrorResponse error = assertInstanceOf(ApiErrorResponse.class, response);
        assertEquals("url", error.getError().getDetails().get("field"));
        verifyNoInteractions(knowledgeBaseService);
    }

    @Test
    void indexJavaRejectsMissingProjectPath() {
        KnowledgeBaseController.IndexJavaRequest request = new KnowledgeBaseController.IndexJavaRequest();

        Object response = controller.indexJavaProject(request);

        ApiErrorResponse error = assertInstanceOf(ApiErrorResponse.class, response);
        assertEquals("projectPath", error.getError().getDetails().get("field"));
        verifyNoInteractions(knowledgeBaseService);
    }

    @Test
    void crawlHuaweiCloudRejectsNonHttpUrl() {
        KnowledgeBaseController.CrawlRequest request = new KnowledgeBaseController.CrawlRequest();
        request.setUrl("ftp://example.com/doc");

        Object response = controller.crawlHuaweiCloudApi(request);

        ApiErrorResponse error = assertInstanceOf(ApiErrorResponse.class, response);
        assertEquals("url", error.getError().getDetails().get("field"));
        verifyNoInteractions(huaweiCloudApiCrawlerService);
    }
}
