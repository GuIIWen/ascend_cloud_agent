package com.agent.service.testcase;

import java.util.Objects;

public class GeneratedTestcaseExecutionRequest {

    private final String resourceProfile;

    public GeneratedTestcaseExecutionRequest(String resourceProfile) {
        this.resourceProfile = Objects.requireNonNull(resourceProfile, "resourceProfile");
    }

    public String getResourceProfile() {
        return resourceProfile;
    }
}
