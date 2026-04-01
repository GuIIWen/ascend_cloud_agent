package com.agent.service.testcase;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class GeneratedTestcaseExecutionServiceImpl implements GeneratedTestcaseExecutionService {

    private static final DateTimeFormatter RUN_ID_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final String OVERALL_STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String OVERALL_STATUS_FAILED = "FAILED";

    private final ResourceProfileRegistry resourceProfileRegistry;
    private final GeneratedTestcaseArtifactRunner artifactRunner;
    private final Path runsRoot;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public GeneratedTestcaseExecutionServiceImpl(
            ResourceProfileRegistry resourceProfileRegistry,
            GeneratedTestcaseArtifactRunner artifactRunner,
            Path runsRoot) {
        this(resourceProfileRegistry, artifactRunner, runsRoot, Clock.systemUTC(), new ObjectMapper().findAndRegisterModules());
    }

    GeneratedTestcaseExecutionServiceImpl(
            ResourceProfileRegistry resourceProfileRegistry,
            GeneratedTestcaseArtifactRunner artifactRunner,
            Path runsRoot,
            Clock clock,
            ObjectMapper objectMapper) {
        this.resourceProfileRegistry = Objects.requireNonNull(resourceProfileRegistry, "resourceProfileRegistry");
        this.artifactRunner = Objects.requireNonNull(artifactRunner, "artifactRunner");
        this.runsRoot = Objects.requireNonNull(runsRoot, "runsRoot");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void assertSupportedResourceProfile(String resourceProfile) {
        resourceProfileRegistry.assertSupported(resourceProfile);
    }

    @Override
    public Set<String> supportedResourceProfiles() {
        return resourceProfileRegistry.supportedProfiles();
    }

    @Override
    public GeneratedTestcaseExecutionResult execute(
            TestcaseGenerationRequest generationRequest,
            TestcaseGenerationResult generationResult,
            GeneratedTestcaseExecutionRequest executionRequest) {
        Objects.requireNonNull(generationRequest, "generationRequest");
        Objects.requireNonNull(generationResult, "generationResult");
        Objects.requireNonNull(executionRequest, "executionRequest");

        String resourceProfile = normalize(executionRequest.getResourceProfile());
        TestResourceLifecycleHandler lifecycleHandler = resourceProfileRegistry.getRequired(resourceProfile);
        String runId = newRunId();
        Path runDirectory = runsRoot.resolve(runId).normalize();
        ensureDirectory(runDirectory);
        GeneratedTestcaseExecutionContext context =
                new GeneratedTestcaseExecutionContext(runId, runDirectory, generationRequest, generationResult);
        writeExecutionInputs(runDirectory, generationRequest, generationResult, resourceProfile);

        GeneratedTestcaseStageResult provision = skipped("Provision stage did not run");
        GeneratedTestcaseStageResult compile = skipped("Compile stage did not run");
        GeneratedTestcaseStageResult test = skipped("Test stage did not run");
        GeneratedTestcaseStageResult release = skipped("Release stage did not run");

        ProvisionedTestResources provisionedResources = null;
        GeneratedTestcaseArtifact artifact = null;

        try {
            provisionedResources = lifecycleHandler.provision(context);
            if (provisionedResources == null) {
                provisionedResources = new ProvisionedTestResources(Map.of(), Map.of(), Map.of());
            }
            provision = success(
                    "Resources provisioned",
                    details(
                            "resourceProfile", resourceProfile,
                            "runDirectory", runDirectory.toString()),
                    provisionedResources.getMetadata());

            artifact = artifactRunner.compile(runDirectory, generationResult.getJavaTestCode());
            compile = success(
                    "Generated testcase compiled",
                    details(
                            "sourceFile", artifact.getSourceFile().toString(),
                            "classesDirectory", artifact.getClassesDirectory().toString(),
                            "compileLogFile", artifact.getCompileLogFile().toString(),
                            "fqcn", artifact.getFqcn()),
                    Map.of());

            Map<String, Object> testDetails = artifactRunner.runTests(
                    runDirectory,
                    artifact,
                    provisionedResources == null ? new ProvisionedTestResources(Map.of(), Map.of(), Map.of()) : provisionedResources);
            test = success(
                    "Generated testcase executed",
                    details("fqcn", artifact.getFqcn()),
                    testDetails);
        } catch (Exception ex) {
            if (GeneratedTestcaseStageResult.STATUS_SKIPPED.equals(provision.getStatus())) {
                provision = failure("Provision stage failed", ex, details(
                        "resourceProfile", resourceProfile,
                        "runDirectory", runDirectory.toString()));
                compile = skipped("Compile stage skipped because provision failed");
                test = skipped("Test stage skipped because provision failed");
            } else if (GeneratedTestcaseStageResult.STATUS_SKIPPED.equals(compile.getStatus())) {
                compile = failure("Compile stage failed", ex, details(
                        "sourceRoot", runDirectory.resolve("src").toString(),
                        "classesDirectory", runDirectory.resolve("classes").toString(),
                        "compileLogFile", runDirectory.resolve("compile.log").toString()));
                test = skipped("Test stage skipped because compilation failed");
            } else if (GeneratedTestcaseStageResult.STATUS_SKIPPED.equals(test.getStatus())) {
                test = failure("Test stage failed", ex, details(
                        "stdoutFile", runDirectory.resolve("test.stdout.log").toString(),
                        "stderrFile", runDirectory.resolve("test.stderr.log").toString(),
                        "fqcn", artifact == null ? null : artifact.getFqcn()));
            }
        } finally {
            if (provisionedResources != null) {
                try {
                    Map<String, Object> releaseDetails = lifecycleHandler.release(provisionedResources, context);
                    release = success(
                            "Resources released",
                            details("resourceProfile", resourceProfile),
                            releaseDetails == null ? Map.of() : releaseDetails);
                } catch (Exception ex) {
                    release = failure("Release stage failed", ex, details(
                            "resourceProfile", resourceProfile,
                            "runDirectory", runDirectory.toString()));
                }
            } else if (GeneratedTestcaseStageResult.STATUS_SKIPPED.equals(provision.getStatus())) {
                release = skipped("Release stage skipped because provision did not run");
            } else if (GeneratedTestcaseStageResult.STATUS_FAILED.equals(provision.getStatus())) {
                release = skipped("Release stage skipped because no resources were provisioned");
            }
        }

        writeExecutionResult(runDirectory, resourceProfile, runId, provision, compile, test, release);
        String overallStatus = isSuccessful(provision, compile, test, release)
                ? OVERALL_STATUS_SUCCEEDED
                : OVERALL_STATUS_FAILED;
        return new GeneratedTestcaseExecutionResult(
                resourceProfile,
                overallStatus,
                runId,
                runDirectory.toString(),
                provision,
                compile,
                test,
                release);
    }

    private boolean isSuccessful(
            GeneratedTestcaseStageResult provision,
            GeneratedTestcaseStageResult compile,
            GeneratedTestcaseStageResult test,
            GeneratedTestcaseStageResult release) {
        return GeneratedTestcaseStageResult.STATUS_SUCCEEDED.equals(provision.getStatus())
                && GeneratedTestcaseStageResult.STATUS_SUCCEEDED.equals(compile.getStatus())
                && GeneratedTestcaseStageResult.STATUS_SUCCEEDED.equals(test.getStatus())
                && GeneratedTestcaseStageResult.STATUS_SUCCEEDED.equals(release.getStatus());
    }

    private GeneratedTestcaseStageResult success(
            String message,
            Map<String, Object> baseDetails,
            Map<String, Object> extraDetails) {
        Instant startedAt = clock.instant();
        Instant finishedAt = clock.instant();
        Map<String, Object> details = new LinkedHashMap<>();
        if (baseDetails != null) {
            details.putAll(baseDetails);
        }
        if (extraDetails != null) {
            details.putAll(extraDetails);
        }
        return new GeneratedTestcaseStageResult(
                GeneratedTestcaseStageResult.STATUS_SUCCEEDED,
                message,
                startedAt.toString(),
                finishedAt.toString(),
                details);
    }

    private GeneratedTestcaseStageResult skipped(String message) {
        Instant now = clock.instant();
        return new GeneratedTestcaseStageResult(
                GeneratedTestcaseStageResult.STATUS_SKIPPED,
                message,
                now.toString(),
                now.toString(),
                Map.of());
    }

    private GeneratedTestcaseStageResult failure(
            String message,
            Exception ex,
            Map<String, Object> details) {
        Instant now = clock.instant();
        Map<String, Object> merged = new LinkedHashMap<>();
        if (details != null) {
            merged.putAll(details);
        }
        merged.put("exceptionType", ex.getClass().getSimpleName());
        merged.put("exceptionMessage", ex.getMessage());
        return new GeneratedTestcaseStageResult(
                GeneratedTestcaseStageResult.STATUS_FAILED,
                message + ": " + ex.getMessage(),
                now.toString(),
                now.toString(),
                merged);
    }

    private void writeExecutionInputs(
            Path runDirectory,
            TestcaseGenerationRequest generationRequest,
            TestcaseGenerationResult generationResult,
            String resourceProfile) {
        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("requirement", generationRequest.getRequirement());
        requestPayload.put("referenceUrl", generationRequest.getReferenceUrl());
        requestPayload.put("expectedHttpStatus", generationRequest.getExpectedHttpStatus());
        requestPayload.put("expectedErrorCode", generationRequest.getExpectedErrorCode());
        requestPayload.put("expectedErrorDescription", generationRequest.getExpectedErrorDescription());
        requestPayload.put("resourceProfile", resourceProfile);

        Map<String, Object> resultPayload = new LinkedHashMap<>();
        resultPayload.put("javaTestCode", generationResult.getJavaTestCode());
        resultPayload.put("citations", generationResult.getCitations());
        resultPayload.put("degraded", generationResult.isDegraded());
        resultPayload.put("refinedRequirement", generationResult.getRefinedRequirement());

        writeJson(runDirectory.resolve("generation-request.json"), requestPayload);
        writeJson(runDirectory.resolve("generation-response.json"), resultPayload);
    }

    private void writeExecutionResult(
            Path runDirectory,
            String resourceProfile,
            String runId,
            GeneratedTestcaseStageResult provision,
            GeneratedTestcaseStageResult compile,
            GeneratedTestcaseStageResult test,
            GeneratedTestcaseStageResult release) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceProfile", resourceProfile);
        payload.put("runId", runId);
        payload.put("provision", toMap(provision));
        payload.put("compile", toMap(compile));
        payload.put("test", toMap(test));
        payload.put("release", toMap(release));
        writeJson(runDirectory.resolve("execution-result.json"), payload);
    }

    private Map<String, Object> toMap(GeneratedTestcaseStageResult stageResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", stageResult.getStatus());
        payload.put("message", stageResult.getMessage());
        payload.put("startedAt", stageResult.getStartedAt());
        payload.put("finishedAt", stageResult.getFinishedAt());
        payload.put("details", stageResult.getDetails());
        return payload;
    }

    private void writeJson(Path file, Object payload) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), payload);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write execution artifact: " + file, ex);
        }
    }

    private String newRunId() {
        return RUN_ID_FORMATTER.format(clock.instant()) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create execution directory: " + directory, ex);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Map<String, Object> details(Object... pairs) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object key = pairs[i];
            Object value = pairs[i + 1];
            if (key == null || value == null) {
                continue;
            }
            details.put(String.valueOf(key), value);
        }
        return details;
    }
}
