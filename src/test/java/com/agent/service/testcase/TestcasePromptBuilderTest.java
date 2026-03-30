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
                "MODELARTS_001",
                "示例错误描述");

        assertTrue(prompt.contains("expectedHttpStatus: 400"));
        assertTrue(prompt.contains("expectedErrorCode: MODELARTS_001"));
        assertTrue(prompt.contains("expectedErrorDescription: 示例错误描述"));
        assertTrue(prompt.contains("断言必须优先使用显式期望"));
        assertTrue(prompt.contains("canonical skeleton"));
        assertTrue(prompt.contains("不要在字段初始化阶段调用 requiredConfig"));
        assertTrue(prompt.contains("必须显式配置 timeout"));
        assertTrue(prompt.contains("禁止 `body.contains(...)`"));
        assertTrue(prompt.contains("显式导入所使用的 JUnit5 注解"));
        assertTrue(prompt.contains("至少包含一个 `@Test` 方法"));
        assertTrue(prompt.contains("import org.junit.jupiter.api.BeforeAll;"));
        assertTrue(prompt.contains("import org.junit.jupiter.api.Test;"));
        assertTrue(prompt.contains("显式负例断言 skeleton 示例"));
        assertTrue(prompt.contains("assertEquals(400, response.statusCode())"));
        assertTrue(prompt.contains("String errorCode = extractErrorCode(response.body())"));
        assertTrue(prompt.contains("String errorDescription = extractErrorDescription(response.body())"));
        assertTrue(prompt.contains("不要写 assertTrue(response.body().contains(\"ModelArts.7000\"))"));
    }

    @Test
    void buildCodeGenerationPromptForbidsFabricationWhenExpectationMissing() {
        String prompt = builder.buildCodeGenerationPrompt(
                "验证删除工作流接口异常返回",
                "source: https://example.com/api",
                false,
                null,
                null,
                null);

        assertTrue(prompt.contains("expectedHttpStatus: not-provided"));
        assertTrue(prompt.contains("expectedErrorCode: not-provided"));
        assertTrue(prompt.contains("expectedErrorDescription: not-provided"));
        assertTrue(prompt.contains("不要臆造状态码、错误码、状态迁移或响应字段断言"));
        assertTrue(prompt.contains("不要硬编码 200/400"));
        assertTrue(prompt.contains("不要臆造具体错误描述"));
        assertTrue(prompt.contains("Optional.ofNullable(requiredConfig(...)).orElse(requiredConfig(...))"));
    }

    @Test
    void buildRefinementPromptIncludesApiAnchorAndExplicitExpectations() {
        String prompt = builder.buildRefinementPrompt(
                "卸载 Lite Server 系统盘",
                """
                        httpMethod: DELETE
                        endpoint: /v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}
                        source: https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html
                        """,
                400,
                "ModelArts.7000",
                "does not support detach volume device");

        assertTrue(prompt.contains("候选API锚点"));
        assertTrue(prompt.contains("/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}"));
        assertTrue(prompt.contains("expectedHttpStatus: 400"));
        assertTrue(prompt.contains("expectedErrorCode: ModelArts.7000"));
        assertTrue(prompt.contains("expectedErrorDescription: does not support detach volume device"));
        assertTrue(prompt.contains("不得切换到其他资源、服务或端点"));
        assertTrue(prompt.contains("输出严格使用以下 4 行格式"));
        assertTrue(prompt.contains("不要照抄接口背景说明、参数约束"));
        assertTrue(prompt.contains("输入行只保留关键入参名"));
        assertTrue(prompt.contains("断言行只能保留已知事实"));
    }

    @Test
    void buildRetryGenerationPromptIncludesTargetedNegativeCaseFixes() {
        String prompt = builder.buildRetryGenerationPrompt(
                "original-prompt",
                "Generated testcase code must assert HTTP status 400 via assertEquals(400, response.statusCode()) or equivalent",
                2);

        assertTrue(prompt.contains("assertEquals(400, response.statusCode())"));

        String wholeBodyPrompt = builder.buildRetryGenerationPrompt(
                "original-prompt",
                "Generated testcase code must not assert explicit error code/description via whole-body contains; parse errorCode/errorDescription into variables and assert those variables instead",
                2);

        assertTrue(wholeBodyPrompt.contains("errorCode"));
        assertTrue(wholeBodyPrompt.contains("errorDescription"));
        assertTrue(wholeBodyPrompt.contains("禁止任何 `body.contains(...)`"));
    }
}
