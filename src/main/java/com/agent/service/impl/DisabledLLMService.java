package com.agent.service.impl;

import com.agent.service.LLMService;

/**
 * 占位LLM服务，明确提示当前未启用远端大模型。
 */
public class DisabledLLMService implements LLMService {

    @Override
    public String generateTestCode(String prompt) {
        throw new IllegalStateException(
                "LLM provider is disabled. Set knowledge-base.llm.provider=custom to enable remote generation.");
    }
}
