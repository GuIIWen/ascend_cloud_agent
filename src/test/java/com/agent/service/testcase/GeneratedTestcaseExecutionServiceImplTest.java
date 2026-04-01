package com.agent.service.testcase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedTestcaseExecutionServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void executeSuccessfulFlowProvisionCompileTestAndRelease() throws Exception {
        RecordingLifecycleHandler lifecycleHandler = new RecordingLifecycleHandler(false);
        RecordingArtifactRunner artifactRunner = RecordingArtifactRunner.success();
        GeneratedTestcaseExecutionServiceImpl service = new GeneratedTestcaseExecutionServiceImpl(
                new ResourceProfileRegistry(List.of(lifecycleHandler)),
                artifactRunner,
                tempDir.resolve(".ascend_agent/generated-testcase-runs"),
                fixedClock(),
                new ObjectMapper().findAndRegisterModules());

        GeneratedTestcaseExecutionResult result = service.execute(
                generationRequest(),
                generationResult(),
                new GeneratedTestcaseExecutionRequest("managed-noop"));

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(GeneratedTestcaseStageResult.STATUS_SUCCEEDED, result.getProvision().getStatus());
        assertEquals(GeneratedTestcaseStageResult.STATUS_SUCCEEDED, result.getCompile().getStatus());
        assertEquals(GeneratedTestcaseStageResult.STATUS_SUCCEEDED, result.getTest().getStatus());
        assertEquals(GeneratedTestcaseStageResult.STATUS_SUCCEEDED, result.getRelease().getStatus());
        assertTrue(lifecycleHandler.provisionCalled);
        assertTrue(lifecycleHandler.releaseCalled);
        assertTrue(artifactRunner.compileCalled);
        assertTrue(artifactRunner.runCalled);
        Path runDirectory = Path.of(result.getRunDirectory());
        assertTrue(runDirectory.startsWith(tempDir.resolve(".ascend_agent/generated-testcase-runs")));
        assertTrue(Files.exists(runDirectory.resolve("generation-request.json")));
        assertTrue(Files.exists(runDirectory.resolve("generation-response.json")));
        assertTrue(Files.exists(runDirectory.resolve("execution-result.json")));
    }

    @Test
    void executeCompileFailureStillReleasesProvisionedResources() {
        RecordingLifecycleHandler lifecycleHandler = new RecordingLifecycleHandler(false);
        RecordingArtifactRunner artifactRunner = RecordingArtifactRunner.compileFailure();
        GeneratedTestcaseExecutionServiceImpl service = new GeneratedTestcaseExecutionServiceImpl(
                new ResourceProfileRegistry(List.of(lifecycleHandler)),
                artifactRunner,
                tempDir.resolve(".ascend_agent/generated-testcase-runs"),
                fixedClock(),
                new ObjectMapper().findAndRegisterModules());

        GeneratedTestcaseExecutionResult result = service.execute(
                generationRequest(),
                generationResult(),
                new GeneratedTestcaseExecutionRequest("managed-noop"));

        assertEquals("FAILED", result.getStatus());
        assertEquals(GeneratedTestcaseStageResult.STATUS_SUCCEEDED, result.getProvision().getStatus());
        assertEquals(GeneratedTestcaseStageResult.STATUS_FAILED, result.getCompile().getStatus());
        assertEquals(GeneratedTestcaseStageResult.STATUS_SKIPPED, result.getTest().getStatus());
        assertEquals(GeneratedTestcaseStageResult.STATUS_SUCCEEDED, result.getRelease().getStatus());
        assertTrue(lifecycleHandler.releaseCalled);
    }

    @Test
    void executeTestFailureStillAttemptsReleaseAndSurfacesReleaseFailure() {
        RecordingLifecycleHandler lifecycleHandler = new RecordingLifecycleHandler(true);
        RecordingArtifactRunner artifactRunner = RecordingArtifactRunner.testFailure();
        GeneratedTestcaseExecutionServiceImpl service = new GeneratedTestcaseExecutionServiceImpl(
                new ResourceProfileRegistry(List.of(lifecycleHandler)),
                artifactRunner,
                tempDir.resolve(".ascend_agent/generated-testcase-runs"),
                fixedClock(),
                new ObjectMapper().findAndRegisterModules());

        GeneratedTestcaseExecutionResult result = service.execute(
                generationRequest(),
                generationResult(),
                new GeneratedTestcaseExecutionRequest("managed-noop"));

        assertEquals("FAILED", result.getStatus());
        assertEquals(GeneratedTestcaseStageResult.STATUS_SUCCEEDED, result.getProvision().getStatus());
        assertEquals(GeneratedTestcaseStageResult.STATUS_SUCCEEDED, result.getCompile().getStatus());
        assertEquals(GeneratedTestcaseStageResult.STATUS_FAILED, result.getTest().getStatus());
        assertEquals(GeneratedTestcaseStageResult.STATUS_FAILED, result.getRelease().getStatus());
        assertTrue(lifecycleHandler.releaseCalled);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-03-30T01:02:03Z"), ZoneOffset.UTC);
    }

    private TestcaseGenerationRequest generationRequest() {
        return new TestcaseGenerationRequest(
                "验证创建实例成功",
                "https://example.com/api",
                200,
                null,
                null);
    }

    private TestcaseGenerationResult generationResult() {
        return new TestcaseGenerationResult(
                """
                        import org.junit.jupiter.api.Test;

                        public class GeneratedSampleTest {
                            @Test
                            void testGenerated() {
                            }
                        }
                        """,
                List.of(TestcaseCitation.referenceUrl("https://example.com/api")),
                false,
                "优化后的测试用例描述");
    }

    private static final class RecordingLifecycleHandler implements TestResourceLifecycleHandler {
        private boolean provisionCalled;
        private boolean releaseCalled;
        private final boolean failRelease;

        private RecordingLifecycleHandler(boolean failRelease) {
            this.failRelease = failRelease;
        }

        @Override
        public String resourceProfile() {
            return "managed-noop";
        }

        @Override
        public ProvisionedTestResources provision(GeneratedTestcaseExecutionContext context) {
            provisionCalled = true;
            LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("provisionedRunId", context.getRunId());
            return new ProvisionedTestResources(Map.of("RUN_ID", context.getRunId()), Map.of(), metadata);
        }

        @Override
        public Map<String, Object> release(
                ProvisionedTestResources provisionedResources,
                GeneratedTestcaseExecutionContext context) {
            releaseCalled = true;
            if (failRelease) {
                throw new IllegalStateException("release failed");
            }
            return Map.of("releasedRunId", context.getRunId());
        }
    }

    private static final class RecordingArtifactRunner implements GeneratedTestcaseArtifactRunner {
        private final RuntimeException compileFailure;
        private final RuntimeException testFailure;
        private boolean compileCalled;
        private boolean runCalled;

        private RecordingArtifactRunner(RuntimeException compileFailure, RuntimeException testFailure) {
            this.compileFailure = compileFailure;
            this.testFailure = testFailure;
        }

        private static RecordingArtifactRunner success() {
            return new RecordingArtifactRunner(null, null);
        }

        private static RecordingArtifactRunner compileFailure() {
            return new RecordingArtifactRunner(new IllegalStateException("compilation exploded"), null);
        }

        private static RecordingArtifactRunner testFailure() {
            return new RecordingArtifactRunner(null, new IllegalStateException("execution exploded"));
        }

        @Override
        public GeneratedTestcaseArtifact compile(Path runDirectory, String javaTestCode) {
            compileCalled = true;
            if (compileFailure != null) {
                throw compileFailure;
            }
            return new GeneratedTestcaseArtifact(
                    "GeneratedSampleTest",
                    runDirectory.resolve("src/GeneratedSampleTest.java"),
                    runDirectory.resolve("classes"),
                    runDirectory.resolve("compile.log"));
        }

        @Override
        public Map<String, Object> runTests(
                Path runDirectory,
                GeneratedTestcaseArtifact artifact,
                ProvisionedTestResources provisionedResources) {
            runCalled = true;
            if (testFailure != null) {
                throw testFailure;
            }
            return Map.of(
                    "stdoutFile", runDirectory.resolve("test.stdout.log").toString(),
                    "stderrFile", runDirectory.resolve("test.stderr.log").toString(),
                    "exitCode", 0);
        }
    }
}
