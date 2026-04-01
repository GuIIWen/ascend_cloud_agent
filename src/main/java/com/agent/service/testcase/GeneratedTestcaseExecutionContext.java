package com.agent.service.testcase;

import java.nio.file.Path;
import java.util.Objects;

public class GeneratedTestcaseExecutionContext {

    private final String runId;
    private final Path runDirectory;
    private final TestcaseGenerationRequest generationRequest;
    private final TestcaseGenerationResult generationResult;

    public GeneratedTestcaseExecutionContext(
            String runId,
            Path runDirectory,
            TestcaseGenerationRequest generationRequest,
            TestcaseGenerationResult generationResult) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.runDirectory = Objects.requireNonNull(runDirectory, "runDirectory");
        this.generationRequest = Objects.requireNonNull(generationRequest, "generationRequest");
        this.generationResult = Objects.requireNonNull(generationResult, "generationResult");
    }

    public String getRunId() {
        return runId;
    }

    public Path getRunDirectory() {
        return runDirectory;
    }

    public TestcaseGenerationRequest getGenerationRequest() {
        return generationRequest;
    }

    public TestcaseGenerationResult getGenerationResult() {
        return generationResult;
    }
}
