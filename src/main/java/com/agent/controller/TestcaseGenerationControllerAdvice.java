package com.agent.controller;

import com.agent.model.error.ApiErrorResponse;
import com.agent.service.testcase.TestcaseReferenceUrlRequiredException;
import com.agent.service.testcase.UnknownResourceProfileException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = TestcaseGenerationController.class)
public class TestcaseGenerationControllerAdvice {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", "Malformed JSON request body or incompatible field type");
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("INVALID_REQUEST", "Request body is invalid", details));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("contentType", ex.getContentType() != null ? ex.getContentType().toString() : "unknown");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json", details));
    }

    @ExceptionHandler(TestcaseReferenceUrlRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleReferenceUrlRequired(TestcaseReferenceUrlRequiredException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(
                        TestcaseReferenceUrlRequiredException.ERROR_CODE,
                        ex.getMessage(),
                        details));
    }

    @ExceptionHandler(UnknownResourceProfileException.class)
    public ResponseEntity<ApiErrorResponse> handleUnknownResourceProfile(UnknownResourceProfileException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(
                        UnknownResourceProfileException.ERROR_CODE,
                        ex.getMessage(),
                        ex.getDetails()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Invalid argument";
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", message);
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("INVALID_ARGUMENT", message, details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "Testcase generation request failed", details));
    }
}
