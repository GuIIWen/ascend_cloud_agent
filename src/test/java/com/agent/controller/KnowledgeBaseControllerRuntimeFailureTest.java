package com.agent.controller;

import com.agent.model.error.ApiErrorResponse;
import com.agent.service.HuaweiCloudApiCrawlerService;
import com.agent.service.KnowledgeBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseControllerRuntimeFailureTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private HuaweiCloudApiCrawlerService huaweiCloudApiCrawlerService;

    private KnowledgeBaseController controller;
    private KnowledgeBaseControllerAdvice advice;

    @BeforeEach
    void setUp() {
        controller = new KnowledgeBaseController(knowledgeBaseService, huaweiCloudApiCrawlerService);
        advice = new KnowledgeBaseControllerAdvice();
    }

    @Test
    void searchRuntimeFailureMapsToStructured500ViaAdvice() {
        KnowledgeBaseController.SearchRequest request = new KnowledgeBaseController.SearchRequest();
        request.setQuery("workflow");
        request.setTopK(5);
        when(knowledgeBaseService.search(eq("workflow"), eq(5)))
                .thenThrow(new RuntimeException("vector search unavailable"));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> controller.search(request));

        ResponseEntity<ApiErrorResponse> response = advice.handleUnexpectedException(thrown);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("INTERNAL_ERROR", response.getBody().getError().getCode());
        assertEquals("RuntimeException", response.getBody().getError().getDetails().get("type"));
    }

    @Test
    void indexRuntimeFailureMapsToStructured500ViaAdvice() {
        KnowledgeBaseController.IndexRequest request = new KnowledgeBaseController.IndexRequest();
        request.setUrl("https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html");
        when(knowledgeBaseService.indexExternalDocs(anyList()))
                .thenThrow(new RuntimeException("vector write unavailable"));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> controller.index(request));

        ResponseEntity<ApiErrorResponse> response = advice.handleUnexpectedException(thrown);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().getError().getCode());
        assertEquals("RuntimeException", response.getBody().getError().getDetails().get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void indexJavaProjectExposesFailureCountForPartialFailure() {
        KnowledgeBaseController.IndexJavaRequest request = new KnowledgeBaseController.IndexJavaRequest();
        request.setProjectPath("/workspace/project");
        when(knowledgeBaseService.indexJavaProject(eq("/workspace/project")))
                .thenReturn(new KnowledgeBaseService.IndexStats(12, 11, 1, 88));

        Object response = controller.indexJavaProject(request);

        Map<String, Object> body = assertInstanceOf(Map.class, response);
        assertEquals(false, body.get("success"));
        assertEquals(12, body.get("totalDocuments"));
        assertEquals(11, body.get("successCount"));
        assertEquals(1, body.get("failureCount"));
        assertEquals(88L, body.get("durationMs"));
    }
}
