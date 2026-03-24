package com.agent.model.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified compatibility-first error body for /api/knowledge/*.
 */
public class ApiErrorResponse {

    private final boolean success;
    private final ApiError error;

    public ApiErrorResponse(boolean success, ApiError error) {
        this.success = success;
        this.error = error;
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, new ApiError(code, message, Collections.emptyMap()));
    }

    public static ApiErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ApiErrorResponse(false, new ApiError(code, message, details));
    }

    public boolean isSuccess() {
        return success;
    }

    public ApiError getError() {
        return error;
    }

    public static class ApiError {
        private final String code;
        private final String message;
        private final Map<String, Object> details;

        public ApiError(String code, String message, Map<String, Object> details) {
            this.code = code;
            this.message = message;
            this.details = details == null ? Collections.emptyMap() : new LinkedHashMap<>(details);
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, Object> getDetails() {
            return Collections.unmodifiableMap(details);
        }
    }
}
