package com.agent.service.testcase;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class UnknownResourceProfileException extends IllegalArgumentException {

    public static final String ERROR_CODE = "UNKNOWN_RESOURCE_PROFILE";

    private final String resourceProfile;
    private final Set<String> supportedProfiles;

    public UnknownResourceProfileException(String resourceProfile, Set<String> supportedProfiles) {
        super(buildMessage(resourceProfile, supportedProfiles));
        this.resourceProfile = resourceProfile;
        this.supportedProfiles = supportedProfiles == null ? Set.of() : Set.copyOf(supportedProfiles);
    }

    public String getResourceProfile() {
        return resourceProfile;
    }

    public Set<String> getSupportedProfiles() {
        return supportedProfiles;
    }

    public Map<String, Object> getDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("field", "execution.resourceProfile");
        details.put("resourceProfile", resourceProfile);
        details.put("supportedProfiles", supportedProfiles);
        return details;
    }

    private static String buildMessage(String resourceProfile, Set<String> supportedProfiles) {
        return "Unknown resourceProfile: " + resourceProfile
                + ". Supported values: " + String.join(", ", supportedProfiles == null ? Set.of() : supportedProfiles);
    }
}
