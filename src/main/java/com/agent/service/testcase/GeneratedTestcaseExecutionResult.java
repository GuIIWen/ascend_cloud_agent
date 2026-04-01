package com.agent.service.testcase;

import java.util.Objects;

public class GeneratedTestcaseExecutionResult {

    private final String resourceProfile;
    private final String status;
    private final String runId;
    private final String runDirectory;
    private final GeneratedTestcaseStageResult provision;
    private final GeneratedTestcaseStageResult compile;
    private final GeneratedTestcaseStageResult test;
    private final GeneratedTestcaseStageResult release;

    public GeneratedTestcaseExecutionResult(
            String resourceProfile,
            String status,
            String runId,
            String runDirectory,
            GeneratedTestcaseStageResult provision,
            GeneratedTestcaseStageResult compile,
            GeneratedTestcaseStageResult test,
            GeneratedTestcaseStageResult release) {
        this.resourceProfile = Objects.requireNonNull(resourceProfile, "resourceProfile");
        this.status = Objects.requireNonNull(status, "status");
        this.runId = Objects.requireNonNull(runId, "runId");
        this.runDirectory = Objects.requireNonNull(runDirectory, "runDirectory");
        this.provision = Objects.requireNonNull(provision, "provision");
        this.compile = Objects.requireNonNull(compile, "compile");
        this.test = Objects.requireNonNull(test, "test");
        this.release = Objects.requireNonNull(release, "release");
    }

    public String getResourceProfile() {
        return resourceProfile;
    }

    public String getStatus() {
        return status;
    }

    public String getRunId() {
        return runId;
    }

    public String getRunDirectory() {
        return runDirectory;
    }

    public GeneratedTestcaseStageResult getProvision() {
        return provision;
    }

    public GeneratedTestcaseStageResult getCompile() {
        return compile;
    }

    public GeneratedTestcaseStageResult getTest() {
        return test;
    }

    public GeneratedTestcaseStageResult getRelease() {
        return release;
    }
}
