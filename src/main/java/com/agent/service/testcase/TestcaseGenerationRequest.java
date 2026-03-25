package com.agent.service.testcase;

/**
 * 测试用例生成请求。
 */
public class TestcaseGenerationRequest {
    private final String requirement;
    private final String referenceUrl;
    private final Integer expectedHttpStatus;
    private final String expectedErrorCode;
    private final String expectedErrorDescription;

    public TestcaseGenerationRequest(String requirement, String referenceUrl) {
        this(requirement, referenceUrl, null, null, null);
    }

    public TestcaseGenerationRequest(
            String requirement,
            String referenceUrl,
            Integer expectedHttpStatus,
            String expectedErrorCode) {
        this(requirement, referenceUrl, expectedHttpStatus, expectedErrorCode, null);
    }

    public TestcaseGenerationRequest(
            String requirement,
            String referenceUrl,
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        this.requirement = requirement;
        this.referenceUrl = referenceUrl;
        this.expectedHttpStatus = expectedHttpStatus;
        this.expectedErrorCode = expectedErrorCode;
        this.expectedErrorDescription = expectedErrorDescription;
    }

    public String getRequirement() {
        return requirement;
    }

    public String getReferenceUrl() {
        return referenceUrl;
    }

    public Integer getExpectedHttpStatus() {
        return expectedHttpStatus;
    }

    public String getExpectedErrorCode() {
        return expectedErrorCode;
    }

    public String getExpectedErrorDescription() {
        return expectedErrorDescription;
    }
}
