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
        assertTrue(prompt.contains("不要臆造具体状态码或错误码"));
        assertTrue(prompt.contains("不要臆造具体错误描述"));
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
        assertTrue(prompt.contains("请写“待确认”，不要臆造"));
    }
}
