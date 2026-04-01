package com.agent.service.testcase;

import java.nio.file.Path;
import java.util.Map;

public interface GeneratedTestcaseArtifactRunner {

    GeneratedTestcaseArtifact compile(Path runDirectory, String javaTestCode);

    Map<String, Object> runTests(
            Path runDirectory,
            GeneratedTestcaseArtifact artifact,
            ProvisionedTestResources provisionedResources);
}
