package com.agent.controller;

import com.agent.model.error.ApiErrorResponse;
import com.agent.model.testcase.TestcaseCitationResponse;
import com.agent.model.testcase.TestcaseGenerateRequest;
import com.agent.model.testcase.TestcaseGenerateResponse;
import com.agent.service.testcase.TestcaseCitation;
import com.agent.service.testcase.TestcaseGenerationRequest;
import com.agent.service.testcase.TestcaseGenerationResult;
import com.agent.service.testcase.TestcaseGenerationService;
import com.agent.service.testcase.TestcaseReferenceUrlRequiredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestcaseGenerationControllerTest {

    @Test
    void generateSuccessReturnsJavaCodeAndMetadata() {
        SuccessService service = new SuccessService();
        TestcaseGenerationController controller = new TestcaseGenerationController(service);
        TestcaseGenerateRequest request = new TestcaseGenerateRequest();
        request.setRequirement("  验证创建实例成功  ");
        request.setReferenceUrl("  https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html  ");

        ResponseEntity<?> response = controller.generate(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TestcaseGenerateResponse body = assertInstanceOf(TestcaseGenerateResponse.class, response.getBody());
        assertEquals("assertEquals(200, response.getStatusCode());", body.getJavaTestCode());
        assertEquals(2, body.getCitations().size());
        assertFalse(body.isDegraded());
        TestcaseCitationResponse firstCitation = body.getCitations().get(0);
        assertEquals(TestcaseCitation.TYPE_KNOWLEDGE_BASE, firstCitation.getType());
        assertEquals("api-modelarts-create", firstCitation.getApiId());
        assertEquals("https://support.huaweicloud.com/api-modelarts/CreateWorkflow.html", firstCitation.getSource());
        TestcaseCitationResponse secondCitation = body.getCitations().get(1);
        assertEquals(TestcaseCitation.TYPE_REFERENCE_URL, secondCitation.getType());
        assertNull(secondCitation.getApiId());
        assertEquals("https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html", secondCitation.getSource());
        assertEquals("验证创建实例成功", service.capturedRequirement);
        assertEquals("https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html", service.capturedReferenceUrl);
    }

    @Test
    void generateRejectsMissingRequirement() {
        GuardService service = new GuardService();
        TestcaseGenerationController controller = new TestcaseGenerationController(service);
        TestcaseGenerateRequest request = new TestcaseGenerateRequest();
        request.setRequirement(" ");
        request.setReferenceUrl("https://example.com/api");

        ResponseEntity<?> response = controller.generate(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse error = assertInstanceOf(ApiErrorResponse.class, response.getBody());
        assertEquals("INVALID_ARGUMENT", error.getError().getCode());
        assertEquals("requirement", error.getError().getDetails().get("field"));
        assertFalse(service.called);
    }

    @Test
    void generatePropagatesKbMissWithoutReferenceUrlError() {
        NoHitWithoutUrlService service = new NoHitWithoutUrlService();
        TestcaseGenerationController controller = new TestcaseGenerationController(service);
        TestcaseGenerationControllerAdvice advice = new TestcaseGenerationControllerAdvice();
        TestcaseGenerateRequest request = new TestcaseGenerateRequest();
        request.setRequirement("验证删除接口返回404");

        TestcaseReferenceUrlRequiredException thrown =
                assertThrows(TestcaseReferenceUrlRequiredException.class, () -> controller.generate(request));

        ResponseEntity<ApiErrorResponse> response = advice.handleReferenceUrlRequired(thrown);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(TestcaseReferenceUrlRequiredException.ERROR_CODE, response.getBody().getError().getCode());
        assertNull(service.capturedReferenceUrl);
        assertTrue(response.getBody().getError().getMessage().contains("referenceUrl"));
    }

    private static final class SuccessService implements TestcaseGenerationService {
        private String capturedRequirement;
        private String capturedReferenceUrl;

        @Override
        public TestcaseGenerationResult generate(TestcaseGenerationRequest request) {
            this.capturedRequirement = request.getRequirement();
            this.capturedReferenceUrl = request.getReferenceUrl();
            return new TestcaseGenerationResult(
                    "assertEquals(200, response.getStatusCode());",
                    List.of(
                            TestcaseCitation.knowledgeBase(
                                    "api-modelarts-create",
                                    "https://support.huaweicloud.com/api-modelarts/CreateWorkflow.html"),
                            TestcaseCitation.referenceUrl(
                                    "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html")),
                    false);
        }
    }

    private static final class GuardService implements TestcaseGenerationService {
        private boolean called;

        @Override
        public TestcaseGenerationResult generate(TestcaseGenerationRequest request) {
            this.called = true;
            return new TestcaseGenerationResult("", List.of(), true);
        }
    }

    private static final class NoHitWithoutUrlService implements TestcaseGenerationService {
        private String capturedReferenceUrl;

        @Override
        public TestcaseGenerationResult generate(TestcaseGenerationRequest request) {
            this.capturedReferenceUrl = request.getReferenceUrl();
            throw new TestcaseReferenceUrlRequiredException();
        }
    }
}
