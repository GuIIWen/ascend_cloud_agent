package com.agent.service.testcase;

import java.util.Map;

public interface TestResourceLifecycleHandler {

    String resourceProfile();

    ProvisionedTestResources provision(GeneratedTestcaseExecutionContext context);

    Map<String, Object> release(
            ProvisionedTestResources provisionedResources,
            GeneratedTestcaseExecutionContext context);
}
