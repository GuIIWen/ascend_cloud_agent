package com.agent.service.testcase;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class GeneratedTestcaseStageResult {

    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    private final String status;
    private final String message;
    private final String startedAt;
    private final String finishedAt;
    private final Map<String, Object> details;

    public GeneratedTestcaseStageResult(
            String status,
            String message,
            String startedAt,
            String finishedAt,
            Map<String, Object> details) {
        this.status = Objects.requireNonNull(status, "status");
        this.message = message;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public Map<String, Object> getDetails() {
        return new LinkedHashMap<>(details);
    }
}
