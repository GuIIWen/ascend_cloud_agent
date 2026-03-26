package com.agent.service.testcase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedTestcasePostProcessorTest {

    private final GeneratedTestcasePostProcessor processor = new GeneratedTestcasePostProcessor();

    @Test
    void rewritesCommonCredentialPlaceholdersToRuntimeConfig() {
        String generated = """
                import org.junit.jupiter.api.Test;

                public class DeleteWorkflowTest {
                    private static final String PROJECT_ID = "project_id_placeholder";
                    private static final String AUTH_TOKEN = "auth_token_placeholder";

                    @Test
                    void testDelete() {
                    }
                }
                """;

        String normalized = processor.process(generated);

        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_PROJECT_ID\", \"hwcloud.project.id\")"));
        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_AUTH_TOKEN\", \"hwcloud.auth.token\")"));
        assertTrue(normalized.contains("Assumptions.assumeTrue"));
        assertFalse(normalized.toLowerCase().contains("placeholder"));
    }

    @Test
    void rewritesResourceIdPlaceholdersToRuntimeConfig() {
        String generated = """
                import org.junit.jupiter.api.Test;

                public class DetachVolumeTest {
                    private static final String DEV_SERVER_ID = "server_id_placeholder";
                    private static final String VOLUME_ID = "volume_id_placeholder";

                    @Test
                    void detach() {
                    }
                }
                """;

        String normalized = processor.process(generated);

        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_DEV_SERVER_ID\", \"hwcloud.dev-server.id\")"));
        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_VOLUME_ID\", \"hwcloud.volume.id\")"));
        assertTrue(normalized.contains("private static String requiredConfig("));
    }

    @Test
    void addsHelperWhenRequiredConfigIsAlreadyUsed() {
        String generated = """
                import org.junit.jupiter.api.Test;

                public class DetachVolumeTest {
                    private static final String DEV_SERVER_ID = requiredConfig("HUAWEICLOUD_DEV_SERVER_ID", "hwcloud.dev-server.id");

                    @Test
                    void detach() {
                    }
                }
                """;

        String normalized = processor.process(generated);

        assertTrue(normalized.contains("private static String requiredConfig("));
        assertTrue(normalized.contains("Assumptions.assumeTrue"));
    }

    @Test
    void rewritesDirectSystemLookupsToRequiredConfig() {
        String generated = """
                import org.junit.jupiter.api.Test;

                public class DetachVolumeTest {
                    private static final String ENV_TOKEN = System.getenv("HUAWEICLOUD_AUTH_TOKEN");
                    private static final String ENV_PROJECT = System.getProperty("hwcloud.project.id");
                    private static final String ENV_BASE = System.getenv("HUAWEICLOUD_BASE_URL");

                    @Test
                    void detach() {
                    }
                }
                """;

        String normalized = processor.process(generated);

        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_AUTH_TOKEN\", \"hwcloud.auth.token\")"));
        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_PROJECT_ID\", \"hwcloud.project.id\")"));
        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_BASE_URL\", \"hwcloud.base.url\")"));
        assertFalse(normalized.contains("System.getenv(\"HUAWEICLOUD_AUTH_TOKEN\")"));
        assertFalse(normalized.contains("System.getProperty(\"hwcloud.project.id\")"));
    }

    @Test
    void rewritesCamelCaseConfigAssignmentsToRequiredConfig() {
        String generated = """
                import java.util.Optional;
                import org.junit.jupiter.api.BeforeAll;
                import org.junit.jupiter.api.Test;

                public class DetachDevServerVolumeTest {
                    private static String authToken;
                    private static String projectId;
                    private static String baseUrl;
                    private static String instanceId;
                    private static String volumeId;

                    @BeforeAll
                    static void loadConfig() {
                        authToken = Optional.ofNullable(System.getenv("HUAWEICLOUD_AUTH_TOKEN")).orElse(System.getProperty("hwcloud.auth.token"));
                        projectId = Optional.ofNullable(System.getenv("HUAWEICLOUD_PROJECT_ID")).orElse(System.getProperty("hwcloud.project.id"));
                        baseUrl = System.getenv("HUAWEICLOUD_BASE_URL");
                        instanceId = "lite-123";
                        volumeId = Optional.ofNullable(System.getenv("HUAWEICLOUD_VOLUME_ID")).orElse(System.getProperty("hwcloud.volume.id"));
                    }

                    @Test
                    void detach() {
                    }
                }
                """;

        String normalized = processor.process(generated);

        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_AUTH_TOKEN\", \"hwcloud.auth.token\")"));
        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_PROJECT_ID\", \"hwcloud.project.id\")"));
        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_BASE_URL\", \"hwcloud.base.url\")"));
        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_INSTANCE_ID\", \"hwcloud.instance.id\")"));
        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_VOLUME_ID\", \"hwcloud.volume.id\")"));
        assertFalse(normalized.contains("System.getenv(\"HUAWEICLOUD_AUTH_TOKEN\")"));
        assertFalse(normalized.contains("lite-123"));
    }

    @Test
    void rewritesInlineDirectConfigLookupOutsideRequiredConfig() {
        String generated = """
                import org.junit.jupiter.api.Test;

                public class DetachVolumeTest {
                    @Test
                    void detach() {
                        System.out.println(System.getenv("HUAWEICLOUD_AUTH_TOKEN"));
                    }
                }
                """;

        String normalized = processor.process(generated);

        assertTrue(normalized.contains("requiredConfig(\"HUAWEICLOUD_AUTH_TOKEN\", \"hwcloud.auth.token\")"));
        assertFalse(normalized.contains("System.getenv(\"HUAWEICLOUD_AUTH_TOKEN\")"));
    }

    @Test
    void rejectsHardCodedResourceIdentifiers() {
        String generated = """
                import org.junit.jupiter.api.Test;

                public class DetachVolumeTest {
                    private static final String DEV_SERVER_ID = "lite-123";
                    private static final String VOLUME_ID = "system";

                    @Test
                    void detach() {
                    }
                }
                """;

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> processor.process(generated));

        assertTrue(error.getMessage().contains("hard-coded resource literal"));
    }

    @Test
    void rejectsNonPublicOrMultipleTopLevelClasses() {
        String packagePrivate = """
                import org.junit.jupiter.api.Test;

                class InvalidTest {
                    @Test
                    void testCase() {
                    }
                }
                """;
        String multipleTypes = """
                import org.junit.jupiter.api.Test;

                public class FirstTest {
                    @Test
                    void testCase() {
                    }
                }

                class SecondTest {
                }
                """;

        IllegalStateException packagePrivateError =
                assertThrows(IllegalStateException.class, () -> processor.process(packagePrivate));
        IllegalStateException multipleTypesError =
                assertThrows(IllegalStateException.class, () -> processor.process(multipleTypes));

        assertEquals("Generated testcase code must declare one public class", packagePrivateError.getMessage());
        assertEquals("Generated testcase code must contain exactly one top-level class", multipleTypesError.getMessage());
    }

    @Test
    void rejectsNonJunit5Class() {
        String generated = """
                public class PlainJavaClass {
                    void run() {
                    }
                }
                """;

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> processor.process(generated));

        assertEquals("Generated testcase code must be a JUnit5 test class", error.getMessage());
    }

    @Test
    void rejectsTodoOutput() {
        String generated = """
                import org.junit.jupiter.api.Test;

                public class InvalidTest {
                    @Test
                    void testTodo() {
                        // TODO implement
                    }
                }
                """;

        assertThrows(IllegalStateException.class, () -> processor.process(generated));
    }

    @Test
    void rejectsInvalidJavaSyntaxEvenWhenParserProducesAst() {
        String generated = """
                import org.junit.jupiter.api.Test;

                public class InvalidTest {
                    @Test
                    void testSyntax() {
                        ???;
                    }
                }
                """;

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> processor.process(generated));

        assertTrue(error.getMessage().contains("not valid Java syntax")
                || error.getMessage().contains("placeholder"));
    }
}
