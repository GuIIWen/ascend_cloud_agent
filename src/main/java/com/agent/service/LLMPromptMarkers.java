package com.agent.service;

/**
 * 标记LLM提示词用途，便于为不同任务设置不同的调用约束。
 */
public final class LLMPromptMarkers {

    public static final String REQUIREMENT_REFINEMENT = "[TASK_MODE=REQUIREMENT_REFINEMENT]";
    public static final String TESTCASE_GENERATION = "[TASK_MODE=TESTCASE_GENERATION]";

    private LLMPromptMarkers() {
    }
}
