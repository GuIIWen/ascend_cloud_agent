package com.agent.service.testcase;

/**
 * 测试用例生成服务。
 */
public interface TestcaseGenerationService {

    /**
     * 生成Java测试用例代码。
     */
    TestcaseGenerationResult generate(TestcaseGenerationRequest request);

    default TestcaseGenerationResult generate(String requirement, String referenceUrl) {
        return generate(new TestcaseGenerationRequest(requirement, referenceUrl));
    }
}
