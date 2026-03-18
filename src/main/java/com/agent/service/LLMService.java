package com.agent.service;

/**
 * LLM代码生成服务接口
 */
public interface LLMService {

    /**
     * 生成测试代码
     * @param prompt 提示词
     * @return 生成的测试代码
     */
    String generateTestCode(String prompt);
}
