package com.agent.controller;

import com.agent.model.error.ApiErrorResponse;
import com.agent.model.testcase.TestcaseCitationResponse;
import com.agent.model.testcase.TestcaseExecutionOptionsRequest;
import com.agent.model.testcase.TestcaseExecutionResponse;
import com.agent.model.testcase.TestcaseGenerateRequest;
import com.agent.model.testcase.TestcaseGenerateResponse;
import com.agent.service.testcase.GeneratedTestcaseExecutionRequest;
import com.agent.service.testcase.GeneratedTestcaseExecutionResult;
import com.agent.service.testcase.GeneratedTestcaseExecutionService;
import com.agent.service.testcase.GeneratedTestcaseStageResult;
import com.agent.service.testcase.TestcaseCitation;
import com.agent.service.testcase.TestcaseGenerationRequest;
import com.agent.service.testcase.TestcaseGenerationResult;
import com.agent.service.testcase.TestcaseGenerationService;
import com.agent.service.testcase.TestcaseReferenceUrlRequiredException;
import com.agent.service.testcase.UnknownResourceProfileException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

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
        request.setExpectedHttpStatus(400);
        request.setExpectedErrorCode("  MODELARTS_001  ");
        request.setExpectedErrorDescription("  示例错误描述  ");

        ResponseEntity<?> response = controller.generate(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TestcaseGenerateResponse body = assertInstanceOf(TestcaseGenerateResponse.class, response.getBody());
        assertEquals("assertEquals(200, response.getStatusCode());", body.getJavaTestCode());
        assertEquals(2, body.getCitations().size());
        assertFalse(body.isDegraded());
        assertEquals("优化后的测试用例描述", body.getRefinedRequirement());
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
        assertEquals(400, service.capturedExpectedHttpStatus);
        assertEquals("MODELARTS_001", service.capturedExpectedErrorCode);
        assertEquals("示例错误描述", service.capturedExpectedErrorDescription);
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
    void generateRejectsInvalidExpectedHttpStatus() {
        GuardService service = new GuardService();
        TestcaseGenerationController controller = new TestcaseGenerationController(service);
        TestcaseGenerateRequest request = new TestcaseGenerateRequest();
        request.setRequirement("验证失败状态");
        request.setExpectedHttpStatus(99);

        ResponseEntity<?> response = controller.generate(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse error = assertInstanceOf(ApiErrorResponse.class, response.getBody());
        assertEquals("INVALID_ARGUMENT", error.getError().getCode());
        assertEquals("expectedHttpStatus", error.getError().getDetails().get("field"));
        assertFalse(service.called);
    }

    @Test
    void generateNormalizesBlankExpectedErrorCodeToNull() {
        SuccessService service = new SuccessService();
        TestcaseGenerationController controller = new TestcaseGenerationController(service);
        TestcaseGenerateRequest request = new TestcaseGenerateRequest();
        request.setRequirement("验证创建实例成功");
        request.setReferenceUrl("https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html");
        request.setExpectedErrorCode("   ");

        ResponseEntity<?> response = controller.generate(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(service.capturedExpectedErrorCode);
    }

    @Test
    void generateNormalizesBlankExpectedErrorDescriptionToNull() {
        SuccessService service = new SuccessService();
        TestcaseGenerationController controller = new TestcaseGenerationController(service);
        TestcaseGenerateRequest request = new TestcaseGenerateRequest();
        request.setRequirement("验证创建实例成功");
        request.setReferenceUrl("https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html");
        request.setExpectedErrorDescription("   ");

        ResponseEntity<?> response = controller.generate(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(service.capturedExpectedErrorDescription);
    }

    @Test
    void generateWithExecutionReturnsStructuredExecutionResult() {
        SuccessService service = new SuccessService();
        SuccessExecutionService executionService = new SuccessExecutionService();
        TestcaseGenerationController controller = new TestcaseGenerationController(service, executionService);
        TestcaseGenerateRequest request = new TestcaseGenerateRequest();
        request.setRequirement("验证创建实例成功");
        TestcaseExecutionOptionsRequest execution = new TestcaseExecutionOptionsRequest();
        execution.setEnabled(true);
        execution.setResourceProfile("managed-noop");
        request.setExecution(execution);

        ResponseEntity<?> response = controller.generate(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TestcaseGenerateResponse body = assertInstanceOf(TestcaseGenerateResponse.class, response.getBody());
        TestcaseExecutionResponse executionBody = body.getExecution();
        assertEquals("managed-noop", executionBody.getResourceProfile());
        assertEquals("SUCCEEDED", executionBody.getStatus());
        assertEquals("SUCCEEDED", executionBody.getProvision().getStatus());
        assertEquals("SUCCEEDED", executionBody.getCompile().getStatus());
        assertEquals("SUCCEEDED", executionBody.getTest().getStatus());
        assertEquals("SUCCEEDED", executionBody.getRelease().getStatus());
        assertEquals("managed-noop", executionService.capturedResourceProfile);
        assertEquals("验证创建实例成功", executionService.capturedRequirement);
    }

    @Test
    void generateRejectsUnknownResourceProfileBeforeGeneration() {
        GuardService service = new GuardService();
        RejectingExecutionService executionService = new RejectingExecutionService();
        TestcaseGenerationController controller = new TestcaseGenerationController(service, executionService);
        TestcaseGenerationControllerAdvice advice = new TestcaseGenerationControllerAdvice();
        TestcaseGenerateRequest request = new TestcaseGenerateRequest();
        request.setRequirement("验证创建实例成功");
        TestcaseExecutionOptionsRequest execution = new TestcaseExecutionOptionsRequest();
        execution.setEnabled(true);
        execution.setResourceProfile("unknown-profile");
        request.setExecution(execution);

        UnknownResourceProfileException thrown =
                assertThrows(UnknownResourceProfileException.class, () -> controller.generate(request));

        ResponseEntity<ApiErrorResponse> response = advice.handleUnknownResourceProfile(thrown);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(UnknownResourceProfileException.ERROR_CODE, response.getBody().getError().getCode());
        assertFalse(service.called);
        assertEquals("unknown-profile", executionService.capturedResourceProfile);
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
        private Integer capturedExpectedHttpStatus;
        private String capturedExpectedErrorCode;
        private String capturedExpectedErrorDescription;

        @Override
        public TestcaseGenerationResult generate(TestcaseGenerationRequest request) {
            this.capturedRequirement = request.getRequirement();
            this.capturedReferenceUrl = request.getReferenceUrl();
            this.capturedExpectedHttpStatus = request.getExpectedHttpStatus();
            this.capturedExpectedErrorCode = request.getExpectedErrorCode();
            this.capturedExpectedErrorDescription = request.getExpectedErrorDescription();
            return new TestcaseGenerationResult(
                    "assertEquals(200, response.getStatusCode());",
                    List.of(
                            TestcaseCitation.knowledgeBase(
                                    "api-modelarts-create",
                                    "https://support.huaweicloud.com/api-modelarts/CreateWorkflow.html"),
                            TestcaseCitation.referenceUrl(
                                    "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html")),
                    false,
                    "优化后的测试用例描述");
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

    private static final class SuccessExecutionService implements GeneratedTestcaseExecutionService {
        private String capturedResourceProfile;
        private String capturedRequirement;

        @Override
        public void assertSupportedResourceProfile(String resourceProfile) {
            this.capturedResourceProfile = resourceProfile;
        }

        @Override
        public Set<String> supportedResourceProfiles() {
            return Set.of("managed-noop");
        }

        @Override
        public GeneratedTestcaseExecutionResult execute(
                TestcaseGenerationRequest generationRequest,
                TestcaseGenerationResult generationResult,
                GeneratedTestcaseExecutionRequest executionRequest) {
            this.capturedRequirement = generationRequest.getRequirement();
            this.capturedResourceProfile = executionRequest.getResourceProfile();
            return new GeneratedTestcaseExecutionResult(
                    executionRequest.getResourceProfile(),
                    "SUCCEEDED",
                    "run-1",
                    "/tmp/run-1",
                    successStage("provision"),
                    successStage("compile"),
                    successStage("test"),
                    successStage("release"));
        }

        private GeneratedTestcaseStageResult successStage(String phase) {
            LinkedHashMap<String, Object> details = new LinkedHashMap<>();
            details.put("phase", phase);
            return new GeneratedTestcaseStageResult(
                    GeneratedTestcaseStageResult.STATUS_SUCCEEDED,
                    phase + " ok",
                    "2026-03-30T00:00:00Z",
                    "2026-03-30T00:00:00Z",
                    details);
        }
    }

    private static final class RejectingExecutionService implements GeneratedTestcaseExecutionService {
        private String capturedResourceProfile;

        @Override
        public void assertSupportedResourceProfile(String resourceProfile) {
            this.capturedResourceProfile = resourceProfile;
            throw new UnknownResourceProfileException(resourceProfile, Set.of("managed-noop"));
        }

        @Override
        public Set<String> supportedResourceProfiles() {
            return Set.of("managed-noop");
        }

        @Override
        public GeneratedTestcaseExecutionResult execute(
                TestcaseGenerationRequest generationRequest,
                TestcaseGenerationResult generationResult,
                GeneratedTestcaseExecutionRequest executionRequest) {
            throw new AssertionError("execute should not be called for unknown resource profile");
        }
    }
}
