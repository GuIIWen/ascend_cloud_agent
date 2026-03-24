package com.agent.controller;

import com.agent.model.error.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeBaseControllerAdviceTest {

    private final KnowledgeBaseControllerAdvice advice = new KnowledgeBaseControllerAdvice();

    @Test
    void mapsMalformedJsonToBadRequest() {
        ResponseEntity<ApiErrorResponse> response =
                advice.handleUnreadableBody(new HttpMessageNotReadableException("Malformed JSON"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_REQUEST", response.getBody().getError().getCode());
    }

    @Test
    void mapsTypeMismatchInRequestBodyToBadRequest() {
        ResponseEntity<ApiErrorResponse> response =
                advice.handleUnreadableBody(new HttpMessageNotReadableException("Cannot deserialize Integer from String"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Request body is invalid", response.getBody().getError().getMessage());
    }

    @Test
    void mapsUnsupportedMediaTypeTo415() {
        ResponseEntity<ApiErrorResponse> response =
                advice.handleUnsupportedMediaType(
                        new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().getError().getCode());
    }

    @Test
    void mapsUnexpectedInternalExceptionTo500() {
        ResponseEntity<ApiErrorResponse> response =
                advice.handleUnexpectedException(new IllegalStateException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().getError().getCode());
        assertEquals("IllegalStateException", response.getBody().getError().getDetails().get("type"));
    }
}
