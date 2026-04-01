package com.agent.controller;

import com.agent.model.error.ApiErrorResponse;
import com.agent.model.testcase.TestcaseCitationResponse;
import com.agent.model.testcase.TestcaseExecutionOptionsRequest;
import com.agent.model.testcase.TestcaseExecutionResponse;
import com.agent.model.testcase.TestcaseExecutionStageResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/testcase")
public class TestcaseGenerationController {

    private final TestcaseGenerationService testcaseGenerationService;
    private final GeneratedTestcaseExecutionService generatedTestcaseExecutionService;

    public TestcaseGenerationController(TestcaseGenerationService testcaseGenerationService) {
        this(testcaseGenerationService, null);
    }

    @Autowired
    public TestcaseGenerationController(
            TestcaseGenerationService testcaseGenerationService,
            GeneratedTestcaseExecutionService generatedTestcaseExecutionService) {
        this.testcaseGenerationService = testcaseGenerationService;
        this.generatedTestcaseExecutionService = generatedTestcaseExecutionService;
    }

    @PostMapping(
            value = "/generate",
            consumes = "application/json",
            produces = "application/json")
    public ResponseEntity<?> generate(@RequestBody TestcaseGenerateRequest request) {
        ApiErrorResponse validationError = validateRequest(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(validationError);
        }

        String requirement = request.getRequirement().trim();
        String referenceUrl = normalizeReferenceUrl(request.getReferenceUrl());
        Integer expectedHttpStatus = normalizeExpectedHttpStatus(request.getExpectedHttpStatus());
        String expectedErrorCode = normalizeExpectedErrorCode(request.getExpectedErrorCode());
        String expectedErrorDescription = normalizeExpectedErrorDescription(request.getExpectedErrorDescription());
        TestcaseGenerationRequest generationRequest = new TestcaseGenerationRequest(
                requirement,
                referenceUrl,
                expectedHttpStatus,
                expectedErrorCode,
                expectedErrorDescription);
        TestcaseGenerationResult result = testcaseGenerationService.generate(generationRequest);
        GeneratedTestcaseExecutionResult executionResult = null;
        if (isExecutionEnabled(request.getExecution())) {
            if (generatedTestcaseExecutionService == null) {
                throw new IllegalStateException("Generated testcase execution service is not configured");
            }
            executionResult = generatedTestcaseExecutionService.execute(
                    generationRequest,
                    result,
                    new GeneratedTestcaseExecutionRequest(normalizeResourceProfile(request.getExecution())));
        }
        return ResponseEntity.ok(toResponse(result, executionResult));
    }

    private ApiErrorResponse validateRequest(TestcaseGenerateRequest request) {
        if (request == null) {
            return validationError("request", "Request body must not be empty");
        }
        if (request.getRequirement() == null || request.getRequirement().trim().isEmpty()) {
            return validationError("requirement", "requirement must not be blank");
        }
        String referenceUrl = request.getReferenceUrl();
        if (referenceUrl != null && !referenceUrl.trim().isEmpty()) {
            String normalized = referenceUrl.trim();
            if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
                return validationError("referenceUrl", "referenceUrl must start with http:// or https://");
            }
        }

        Integer expectedHttpStatus = request.getExpectedHttpStatus();
        if (expectedHttpStatus != null && (expectedHttpStatus < 100 || expectedHttpStatus > 599)) {
            return validationError("expectedHttpStatus", "expectedHttpStatus must be between 100 and 599");
        }
        TestcaseExecutionOptionsRequest execution = request.getExecution();
        if (execution != null) {
            String resourceProfile = normalizeResourceProfile(execution);
            if (Boolean.TRUE.equals(execution.getEnabled()) && resourceProfile == null) {
                return validationError(
                        "execution.resourceProfile",
                        "execution.resourceProfile must not be blank when execution.enabled is true");
            }
            if (resourceProfile != null) {
                if (generatedTestcaseExecutionService == null) {
                    throw new IllegalStateException("Generated testcase execution service is not configured");
                }
                generatedTestcaseExecutionService.assertSupportedResourceProfile(resourceProfile);
            }
        }
        return null;
    }

    private ApiErrorResponse validationError(String field, String message) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("field", field);
        return ApiErrorResponse.of("INVALID_ARGUMENT", message, details);
    }

    private String normalizeReferenceUrl(String referenceUrl) {
        if (referenceUrl == null) {
            return null;
        }
        String normalized = referenceUrl.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Integer normalizeExpectedHttpStatus(Integer expectedHttpStatus) {
        return expectedHttpStatus;
    }

    private String normalizeExpectedErrorCode(String expectedErrorCode) {
        if (expectedErrorCode == null) {
            return null;
        }
        String normalized = expectedErrorCode.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeExpectedErrorDescription(String expectedErrorDescription) {
        if (expectedErrorDescription == null) {
            return null;
        }
        String normalized = expectedErrorDescription.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeResourceProfile(TestcaseExecutionOptionsRequest execution) {
        if (execution == null || execution.getResourceProfile() == null) {
            return null;
        }
        String normalized = execution.getResourceProfile().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isExecutionEnabled(TestcaseExecutionOptionsRequest execution) {
        return execution != null && Boolean.TRUE.equals(execution.getEnabled());
    }

    private TestcaseGenerateResponse toResponse(
            TestcaseGenerationResult result,
            GeneratedTestcaseExecutionResult executionResult) {
        if (result == null) {
            throw new IllegalStateException("Testcase generation result must not be null");
        }
        return new TestcaseGenerateResponse(
                result.getJavaTestCode(),
                toCitationResponses(result.getCitations()),
                result.isDegraded(),
                result.getRefinedRequirement(),
                toExecutionResponse(executionResult));
    }

    private TestcaseExecutionResponse toExecutionResponse(GeneratedTestcaseExecutionResult executionResult) {
        if (executionResult == null) {
            return null;
        }
        return new TestcaseExecutionResponse(
                executionResult.getResourceProfile(),
                executionResult.getStatus(),
                executionResult.getRunId(),
                executionResult.getRunDirectory(),
                toExecutionStageResponse(executionResult.getProvision()),
                toExecutionStageResponse(executionResult.getCompile()),
                toExecutionStageResponse(executionResult.getTest()),
                toExecutionStageResponse(executionResult.getRelease()));
    }

    private TestcaseExecutionStageResponse toExecutionStageResponse(GeneratedTestcaseStageResult stageResult) {
        if (stageResult == null) {
            return null;
        }
        return new TestcaseExecutionStageResponse(
                stageResult.getStatus(),
                stageResult.getMessage(),
                stageResult.getStartedAt(),
                stageResult.getFinishedAt(),
                stageResult.getDetails());
    }

    private List<TestcaseCitationResponse> toCitationResponses(List<TestcaseCitation> citations) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        List<TestcaseCitationResponse> responses = new ArrayList<>(citations.size());
        for (TestcaseCitation citation : citations) {
            responses.add(new TestcaseCitationResponse(
                    citation.getType(),
                    citation.getApiId(),
                    citation.getSource()));
        }
        return responses;
    }
}
