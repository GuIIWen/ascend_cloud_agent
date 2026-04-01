package com.agent.model.testcase;

public class TestcaseGenerateRequest {

    private String requirement;
    private String referenceUrl;
    private Integer expectedHttpStatus;
    private String expectedErrorCode;
    private String expectedErrorDescription;
    private TestcaseExecutionOptionsRequest execution;

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public String getReferenceUrl() {
        return referenceUrl;
    }

    public void setReferenceUrl(String referenceUrl) {
        this.referenceUrl = referenceUrl;
    }

    public Integer getExpectedHttpStatus() {
        return expectedHttpStatus;
    }

    public void setExpectedHttpStatus(Integer expectedHttpStatus) {
        this.expectedHttpStatus = expectedHttpStatus;
    }

    public String getExpectedErrorCode() {
        return expectedErrorCode;
    }

    public void setExpectedErrorCode(String expectedErrorCode) {
        this.expectedErrorCode = expectedErrorCode;
    }

    public String getExpectedErrorDescription() {
        return expectedErrorDescription;
    }

    public void setExpectedErrorDescription(String expectedErrorDescription) {
        this.expectedErrorDescription = expectedErrorDescription;
    }

    public TestcaseExecutionOptionsRequest getExecution() {
        return execution;
    }

    public void setExecution(TestcaseExecutionOptionsRequest execution) {
        this.execution = execution;
    }
}
