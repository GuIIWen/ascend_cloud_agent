package com.agent.controller;

import com.agent.model.error.ApiErrorResponse;
import com.agent.service.testcase.TestcaseReferenceUrlRequiredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestcaseGenerationControllerAdviceTest {

    private final TestcaseGenerationControllerAdvice advice = new TestcaseGenerationControllerAdvice();

    @Test
    void mapsKbMissWithoutReferenceUrlToBadRequest() {
        ResponseEntity<ApiErrorResponse> response =
                advice.handleReferenceUrlRequired(new TestcaseReferenceUrlRequiredException());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(TestcaseReferenceUrlRequiredException.ERROR_CODE, response.getBody().getError().getCode());
        assertEquals("No related API found in knowledge base. Please provide referenceUrl to generate Java testcase code.",
                response.getBody().getError().getMessage());
    }

    @Test
    void mapsGenericIllegalArgumentToBadRequest() {
        ResponseEntity<ApiErrorResponse> response =
                advice.handleIllegalArgument(new IllegalArgumentException("requirement must not be blank"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_ARGUMENT", response.getBody().getError().getCode());
        assertEquals("requirement must not be blank", response.getBody().getError().getMessage());
    }
}
