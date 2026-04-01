package com.agent.service.testcase;

import java.util.LinkedHashMap;
import java.util.Map;

public class NoopTestResourceLifecycleHandler implements TestResourceLifecycleHandler {

    public static final String RESOURCE_PROFILE = "managed-noop";

    @Override
    public String resourceProfile() {
        return RESOURCE_PROFILE;
    }

    @Override
    public ProvisionedTestResources provision(GeneratedTestcaseExecutionContext context) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("handler", "noop");
        metadata.put("resourceProfile", RESOURCE_PROFILE);
        metadata.put("runId", context.getRunId());
        return new ProvisionedTestResources(Map.of(), Map.of(), metadata);
    }

    @Override
    public Map<String, Object> release(
            ProvisionedTestResources provisionedResources,
            GeneratedTestcaseExecutionContext context) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("handler", "noop");
        metadata.put("resourceProfile", RESOURCE_PROFILE);
        metadata.put("released", true);
        metadata.put("runId", context.getRunId());
        return metadata;
    }
}
