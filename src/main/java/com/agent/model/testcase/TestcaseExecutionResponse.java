package com.agent.model.testcase;

public class TestcaseExecutionResponse {

    private String resourceProfile;
    private String status;
    private String runId;
    private String runDirectory;
    private TestcaseExecutionStageResponse provision;
    private TestcaseExecutionStageResponse compile;
    private TestcaseExecutionStageResponse test;
    private TestcaseExecutionStageResponse release;

    public TestcaseExecutionResponse() {
    }

    public TestcaseExecutionResponse(
            String resourceProfile,
            String status,
            String runId,
            String runDirectory,
            TestcaseExecutionStageResponse provision,
            TestcaseExecutionStageResponse compile,
            TestcaseExecutionStageResponse test,
            TestcaseExecutionStageResponse release) {
        this.resourceProfile = resourceProfile;
        this.status = status;
        this.runId = runId;
        this.runDirectory = runDirectory;
        this.provision = provision;
        this.compile = compile;
        this.test = test;
        this.release = release;
    }

    public String getResourceProfile() {
        return resourceProfile;
    }

    public void setResourceProfile(String resourceProfile) {
        this.resourceProfile = resourceProfile;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getRunDirectory() {
        return runDirectory;
    }

    public void setRunDirectory(String runDirectory) {
        this.runDirectory = runDirectory;
    }

    public TestcaseExecutionStageResponse getProvision() {
        return provision;
    }

    public void setProvision(TestcaseExecutionStageResponse provision) {
        this.provision = provision;
    }

    public TestcaseExecutionStageResponse getCompile() {
        return compile;
    }

    public void setCompile(TestcaseExecutionStageResponse compile) {
        this.compile = compile;
    }

    public TestcaseExecutionStageResponse getTest() {
        return test;
    }

    public void setTest(TestcaseExecutionStageResponse test) {
        this.test = test;
    }

    public TestcaseExecutionStageResponse getRelease() {
        return release;
    }

    public void setRelease(TestcaseExecutionStageResponse release) {
        this.release = release;
    }
}
