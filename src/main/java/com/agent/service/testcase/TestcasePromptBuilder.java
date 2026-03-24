package com.agent.service.testcase;

/**
 * 构建测试用例链路中的提示词。
 */
public class TestcasePromptBuilder {

    String buildRefinementPrompt(String requirement) {
        return """
                你是测试需求优化助手。请把下面的测试需求重写为更清晰、可检索、可生成Java测试代码的描述。
                约束：
                1) 保留原始业务目标和验收点，不得凭空新增接口。
                2) 明确前置条件、输入、步骤、断言。
                3) 输出中文纯文本，不要使用Markdown，不要输出额外解释。

                原始需求：
                %s
                """.formatted(requirement);
    }

    String buildCodeGenerationPrompt(String refinedRequirement, String context, boolean knowledgeBaseHit) {
        String sourceMode = knowledgeBaseHit ? "knowledge-base-rag" : "reference-url-fallback";
        return """
                你是Java测试工程师，请根据需求和上下文直接生成可编译的JUnit5测试类代码。
                约束：
                1) 只输出Java代码，不要Markdown代码块，不要解释。
                2) 不能输出TODO、伪代码或占位符。
                3) 测试中要包含清晰断言。
                4) 优先使用上下文中的接口、字段和约束。
                5) 若上下文中有HTTP接口信息，可使用常见HTTP客户端写法。

                来源模式：%s
                优化后的测试需求：
                %s

                可用上下文：
                %s
                """.formatted(sourceMode, refinedRequirement, context);
    }
}
