package com.agent.service.testcase;

import com.agent.service.LLMPromptMarkers;

/**
 * 构建测试用例链路中的提示词。
 */
public class TestcasePromptBuilder {

    String buildRefinementPrompt(
            String requirement,
            String apiAnchor,
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        String statusHint = expectedHttpStatus == null ? "not-provided" : expectedHttpStatus.toString();
        String errorCodeHint = hasText(expectedErrorCode) ? expectedErrorCode : "not-provided";
        String errorDescriptionHint = hasText(expectedErrorDescription) ? expectedErrorDescription : "not-provided";
        String apiAnchorHint = hasText(apiAnchor) ? apiAnchor : "not-provided";
        return """
                %s
                你是测试需求优化助手。请把下面的测试需求重写为更清晰、可检索、可生成Java测试代码的描述。
                约束：
                1) 保留原始业务目标、动作对象和验收点，不得凭空新增、删除或替换接口/API。
                2) 如果下方提供了“候选API锚点”，只能围绕该锚点重写，不得切换到其他资源、服务或端点。
                3) 候选API锚点只用于识别 HTTP 方法、endpoint 和关键参数名；不要照抄接口背景说明、参数约束、来源链接或大段文档原文。
                4) 如果下方提供了 expectedHttpStatus / expectedErrorCode / expectedErrorDescription，必须原样保留这些显式期望，不得自行改写。
                5) 输出严格使用以下 4 行格式，缺失项写“待确认”：
                   前置条件：...
                   输入：...
                   步骤：...
                   断言：...
                6) 每行只保留最关键的信息，优先短句，总体保持精炼，不要展开成长段说明。
                7) 输入行只保留关键入参名，不要复制参数解释、取值范围、约束限制。
                8) 步骤行优先写“调用 <HTTP方法> <endpoint> 接口”，如果锚点缺失再写“调用目标接口”。
                9) 如果显式期望缺失，断言行只能保留已知事实；状态码、错误码、错误描述、响应字段或后续资源状态未知时统一写“待确认”，不要写“成功卸载”“系统盘消失”等推断结果。
                10) 严禁臆造请求体字段、device 路径、资源状态、错误码或错误描述。
                11) 输出中文纯文本，不要使用Markdown，不要输出额外解释。

                原始需求：
                %s

                显式期望：
                - expectedHttpStatus: %s
                - expectedErrorCode: %s
                - expectedErrorDescription: %s

                候选API锚点：
                %s
                """.formatted(
                LLMPromptMarkers.REQUIREMENT_REFINEMENT,
                requirement,
                statusHint,
                errorCodeHint,
                errorDescriptionHint,
                apiAnchorHint);
    }

    String buildCodeGenerationPrompt(
            String refinedRequirement,
            String context,
            boolean knowledgeBaseHit,
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        String sourceMode = knowledgeBaseHit ? "knowledge-base-rag" : "reference-url-fallback";
        String statusHint = expectedHttpStatus == null ? "not-provided" : expectedHttpStatus.toString();
        String errorCodeHint = hasText(expectedErrorCode) ? expectedErrorCode : "not-provided";
        String errorDescriptionHint = hasText(expectedErrorDescription) ? expectedErrorDescription : "not-provided";
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
                7) 生成结果必须是一个且仅一个 public class，使用 JUnit5 注解，能被 Java 21 直接编译。
                8) 所有路径参数或资源标识字段必须走 requiredConfig(...)，至少包括 DEV_SERVER_ID / SERVER_ID / INSTANCE_ID / VOLUME_ID / DISK_ID，不得硬编码诸如 lite-123、system、demo-id 之类字面量。
                9) 如果上下文只给出了一个 API，只能围绕这一个 API 生成测试，不要自行扩展额外接口做二次校验。
                10) 如果上下文未明确给出成功/失败真值，不要臆造状态码、错误码、状态迁移或响应字段断言，也不要硬编码 200/400 或 body.contains("operation_id") 这类断言。
                11) 若需要鉴权，请优先使用以下约定：
                    - 环境变量：HUAWEICLOUD_AUTH_TOKEN、HUAWEICLOUD_PROJECT_ID、HUAWEICLOUD_BASE_URL
                    - 系统属性：hwcloud.auth.token、hwcloud.project.id、hwcloud.base.url
                12) 资源标识若需要运行时传入，请优先使用以下约定：
                    - DEV_SERVER_ID: HUAWEICLOUD_DEV_SERVER_ID / hwcloud.dev-server.id
                    - SERVER_ID: HUAWEICLOUD_SERVER_ID / hwcloud.server.id
                    - INSTANCE_ID: HUAWEICLOUD_INSTANCE_ID / hwcloud.instance.id
                    - VOLUME_ID: HUAWEICLOUD_VOLUME_ID / hwcloud.volume.id
                    - DISK_ID: HUAWEICLOUD_DISK_ID / hwcloud.disk.id
                13) 如果上下文中出现 pathParamBinding: <param> -> <binding>，必须严格按该绑定读取变量；例如 /dev-servers/{id} 的 id 必须使用 DEV_SERVER_ID，不得替换成 SERVER_ID。
                14) 如果缺少必要运行参数，可在测试中使用 JUnit5 Assumptions 跳过，不要输出假的默认值。
                15) 只生成一个最小但完整的测试类，避免无关辅助代码。
                16) 如果显式期望已提供（expectedHttpStatus / expectedErrorCode / expectedErrorDescription），断言必须优先使用显式期望，不得被模型自行改写。
                17) 如果显式状态码或错误码未提供，且上下文没有明确状态码/错误码，不要臆造具体状态码或错误码。
                18) 如果 expectedErrorDescription 未提供，且上下文没有明确错误描述或明确响应字段，不要臆造具体错误描述，也不要补写 body.contains("operation_id") 等字段断言。

                来源模式：%s
                显式期望：
                - expectedHttpStatus: %s
                - expectedErrorCode: %s
                - expectedErrorDescription: %s
                优化后的测试需求：
                %s

                可用上下文：
                %s
                """.formatted(
                LLMPromptMarkers.TESTCASE_GENERATION,
                sourceMode,
                statusHint,
                errorCodeHint,
                errorDescriptionHint,
                refinedRequirement,
                context);
    }

    String buildRetryGenerationPrompt(String originalPrompt, String validationError, int attempt) {
        return """
                %s

                上一次生成结果未通过自动校验，这是第 %d 次重试。
                必须修正的问题：
                - %s

                额外强约束：
                1) 只输出完整 Java 代码，不要解释。
                2) 所有配置读取统一使用 requiredConfig(...)，不要直接写 System.getenv(\"HUAWEICLOUD_...\") 或 System.getProperty(\"hwcloud....\")。
                3) 如果上下文只给了一个 API，不得扩展第二个 API。
                """.formatted(originalPrompt, attempt, validationError);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
