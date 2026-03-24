package com.agent.service.testcase;

/**
 * 测试用例生成请求。
 */
public class TestcaseGenerationRequest {
    private final String requirement;
    private final String referenceUrl;

    public TestcaseGenerationRequest(String requirement, String referenceUrl) {
        this.requirement = requirement;
        this.referenceUrl = referenceUrl;
    }

    public String getRequirement() {
        return requirement;
    }

    public String getReferenceUrl() {
        return referenceUrl;
    }
}
