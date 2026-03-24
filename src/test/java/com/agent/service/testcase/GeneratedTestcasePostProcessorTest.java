package com.agent.service.testcase;

import org.junit.jupiter.api.Test;

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
}
