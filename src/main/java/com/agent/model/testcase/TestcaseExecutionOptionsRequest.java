package com.agent.model.testcase;

public class TestcaseExecutionOptionsRequest {

    private Boolean enabled;
    private String resourceProfile;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getResourceProfile() {
        return resourceProfile;
    }

    public void setResourceProfile(String resourceProfile) {
        this.resourceProfile = resourceProfile;
    }
}
