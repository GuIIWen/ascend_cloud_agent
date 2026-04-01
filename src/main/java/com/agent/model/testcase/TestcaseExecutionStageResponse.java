package com.agent.model.testcase;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestcaseExecutionStageResponse {

    private String status;
    private String message;
    private String startedAt;
    private String finishedAt;
    private Map<String, Object> details = new LinkedHashMap<>();

    public TestcaseExecutionStageResponse() {
    }

    public TestcaseExecutionStageResponse(
            String status,
            String message,
            String startedAt,
            String finishedAt,
            Map<String, Object> details) {
        this.status = status;
        this.message = message;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        setDetails(details);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Map<String, Object> getDetails() {
        return Collections.unmodifiableMap(details);
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
    }
}
