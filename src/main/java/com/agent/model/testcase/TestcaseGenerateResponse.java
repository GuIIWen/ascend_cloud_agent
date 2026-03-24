package com.agent.model.testcase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestcaseGenerateResponse {

    private String javaTestCode;
    private List<TestcaseCitationResponse> citations = new ArrayList<>();
    private boolean degraded;

    public TestcaseGenerateResponse() {
    }

    public TestcaseGenerateResponse(String javaTestCode, List<TestcaseCitationResponse> citations, boolean degraded) {
        this.javaTestCode = javaTestCode;
        setCitations(citations);
        this.degraded = degraded;
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
}
