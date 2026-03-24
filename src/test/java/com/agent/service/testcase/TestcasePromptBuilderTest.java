package com.agent.service.testcase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestcasePromptBuilderTest {

    private final TestcasePromptBuilder builder = new TestcasePromptBuilder();

    @Test
    void buildCodeGenerationPromptIncludesExplicitExpectations() {
        String prompt = builder.buildCodeGenerationPrompt(
                "验证删除工作流接口异常返回",
                "source: https://example.com/api",
                false,
                400,
                "MODELARTS_001");

        assertTrue(prompt.contains("expectedHttpStatus: 400"));
        assertTrue(prompt.contains("expectedErrorCode: MODELARTS_001"));
        assertTrue(prompt.contains("显式期望已提供，断言必须优先使用显式期望"));
    }

    @Test
    void buildCodeGenerationPromptForbidsFabricationWhenExpectationMissing() {
        String prompt = builder.buildCodeGenerationPrompt(
                "验证删除工作流接口异常返回",
                "source: https://example.com/api",
                false,
                null,
                null);

        assertTrue(prompt.contains("expectedHttpStatus: not-provided"));
        assertTrue(prompt.contains("expectedErrorCode: not-provided"));
        assertTrue(prompt.contains("不要臆造具体状态码或错误码"));
    }
}
