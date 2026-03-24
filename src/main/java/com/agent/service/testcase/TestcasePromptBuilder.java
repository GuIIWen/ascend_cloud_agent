package com.agent.service.testcase;

import com.agent.service.LLMPromptMarkers;

/**
 * 构建测试用例链路中的提示词。
 */
public class TestcasePromptBuilder {

    String buildRefinementPrompt(String requirement) {
        return """
                %s
                你是测试需求优化助手。请把下面的测试需求重写为更清晰、可检索、可生成Java测试代码的描述。
                约束：
                1) 保留原始业务目标和验收点，不得凭空新增接口。
                2) 明确前置条件、输入、步骤、断言。
                3) 输出中文纯文本，不要使用Markdown，不要输出额外解释。
                4) 输出尽量精炼，控制在 200 字以内。

                原始需求：
                %s
                """.formatted(LLMPromptMarkers.REQUIREMENT_REFINEMENT, requirement);
    }

    String buildCodeGenerationPrompt(
            String refinedRequirement,
            String context,
            boolean knowledgeBaseHit,
            Integer expectedHttpStatus,
            String expectedErrorCode) {
        String sourceMode = knowledgeBaseHit ? "knowledge-base-rag" : "reference-url-fallback";
        String statusHint = expectedHttpStatus == null ? "not-provided" : expectedHttpStatus.toString();
        String errorCodeHint = hasText(expectedErrorCode) ? expectedErrorCode : "not-provided";
        return """
                %s
                你是Java测试工程师，请根据需求和上下文直接生成可编译的JUnit5测试类代码。
                约束：
                1) 只输出Java代码，不要Markdown代码块，不要解释。
                2) 不能输出TODO、伪代码或占位符字符串，例如 auth_token_placeholder、project_id_placeholder、your_xxx。
                3) 测试中要包含清晰断言。
                4) 优先使用上下文中的接口、字段和约束。
                5) 若上下文中有HTTP接口信息，可使用常见HTTP客户端写法。
                6) 认证、项目ID、区域等运行参数不要写死成占位符常量，统一从环境变量或系统属性读取。
                7) 若需要鉴权，请优先使用以下约定：
                   - 环境变量：HUAWEICLOUD_AUTH_TOKEN、HUAWEICLOUD_PROJECT_ID、HUAWEICLOUD_BASE_URL
                   - 系统属性：hwcloud.auth.token、hwcloud.project.id、hwcloud.base.url
                8) 如果缺少必要运行参数，可在测试中使用 JUnit5 Assumptions 跳过，不要输出假的默认值。
                9) 只生成一个最小但完整的测试类，避免无关辅助代码。
                10) 如果显式期望已提供，断言必须优先使用显式期望，不得被模型自行改写。
                11) 如果显式期望未提供，且上下文没有明确状态码/错误码，不要臆造具体状态码或错误码；可以用注释说明待确认。

                来源模式：%s
                显式期望：
                - expectedHttpStatus: %s
                - expectedErrorCode: %s
                优化后的测试需求：
                %s

                可用上下文：
                %s
                """.formatted(
                LLMPromptMarkers.TESTCASE_GENERATION,
                sourceMode,
                statusHint,
                errorCodeHint,
                refinedRequirement,
                context);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
