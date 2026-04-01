package com.agent.model.testcase;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestcaseGenerateResponse {

    private String javaTestCode;
    private List<TestcaseCitationResponse> citations = new ArrayList<>();
    private boolean degraded;
    private String refinedRequirement;
    private TestcaseExecutionResponse execution;

    public TestcaseGenerateResponse() {
    }

    public TestcaseGenerateResponse(
            String javaTestCode,
            List<TestcaseCitationResponse> citations,
            boolean degraded,
            String refinedRequirement) {
        this(javaTestCode, citations, degraded, refinedRequirement, null);
    }

    public TestcaseGenerateResponse(
            String javaTestCode,
            List<TestcaseCitationResponse> citations,
            boolean degraded,
            String refinedRequirement,
            TestcaseExecutionResponse execution) {
        this.javaTestCode = javaTestCode;
        setCitations(citations);
        this.degraded = degraded;
        this.refinedRequirement = refinedRequirement;
        this.execution = execution;
    }

    public String getJavaTestCode() {
        return javaTestCode;
    }

    public void setJavaTestCode(String javaTestCode) {
        this.javaTestCode = javaTestCode;
    }

    public List<TestcaseCitationResponse> getCitations() {
        return Collections.unmodifiableList(citations);
    }

    public void setCitations(List<TestcaseCitationResponse> citations) {
        this.citations = citations == null ? new ArrayList<>() : new ArrayList<>(citations);
    }

    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    public String getRefinedRequirement() {
        return refinedRequirement;
    }

    public void setRefinedRequirement(String refinedRequirement) {
        this.refinedRequirement = refinedRequirement;
    }

    public TestcaseExecutionResponse getExecution() {
        return execution;
    }

    public void setExecution(TestcaseExecutionResponse execution) {
        this.execution = execution;
    }
}
