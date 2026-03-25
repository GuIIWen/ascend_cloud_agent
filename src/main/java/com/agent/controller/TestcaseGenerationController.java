package com.agent.controller;

import com.agent.model.error.ApiErrorResponse;
import com.agent.model.testcase.TestcaseCitationResponse;
import com.agent.model.testcase.TestcaseGenerateRequest;
import com.agent.model.testcase.TestcaseGenerateResponse;
import com.agent.service.testcase.TestcaseCitation;
import com.agent.service.testcase.TestcaseGenerationRequest;
import com.agent.service.testcase.TestcaseGenerationResult;
import com.agent.service.testcase.TestcaseGenerationService;
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

    public TestcaseGenerationController(TestcaseGenerationService testcaseGenerationService) {
        this.testcaseGenerationService = testcaseGenerationService;
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
        TestcaseGenerationResult result = testcaseGenerationService.generate(
                new TestcaseGenerationRequest(
                        requirement,
                        referenceUrl,
                        expectedHttpStatus,
                        expectedErrorCode,
                        expectedErrorDescription));
        return ResponseEntity.ok(toResponse(result));
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

    private TestcaseGenerateResponse toResponse(TestcaseGenerationResult result) {
        if (result == null) {
            throw new IllegalStateException("Testcase generation result must not be null");
        }
        return new TestcaseGenerateResponse(
                result.getJavaTestCode(),
                toCitationResponses(result.getCitations()),
                result.isDegraded());
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
