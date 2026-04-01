package com.agent.service.testcase;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProvisionedTestResources {

    private final Map<String, String> environmentVariables;
    private final Map<String, String> systemProperties;
    private final Map<String, Object> metadata;

    public ProvisionedTestResources(
            Map<String, String> environmentVariables,
            Map<String, String> systemProperties,
            Map<String, Object> metadata) {
        this.environmentVariables = environmentVariables == null
                ? Map.of()
                : Map.copyOf(environmentVariables);
        this.systemProperties = systemProperties == null
                ? Map.of()
                : Map.copyOf(systemProperties);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Map<String, String> getEnvironmentVariables() {
        return new LinkedHashMap<>(environmentVariables);
    }

    public Map<String, String> getSystemProperties() {
        return new LinkedHashMap<>(systemProperties);
    }

    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }
}
