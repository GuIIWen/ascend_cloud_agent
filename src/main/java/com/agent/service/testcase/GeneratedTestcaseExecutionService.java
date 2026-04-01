package com.agent.service.testcase;

import java.util.Set;

public interface GeneratedTestcaseExecutionService {

    void assertSupportedResourceProfile(String resourceProfile);

    Set<String> supportedResourceProfiles();

    GeneratedTestcaseExecutionResult execute(
            TestcaseGenerationRequest generationRequest,
            TestcaseGenerationResult generationResult,
            GeneratedTestcaseExecutionRequest executionRequest);
}
