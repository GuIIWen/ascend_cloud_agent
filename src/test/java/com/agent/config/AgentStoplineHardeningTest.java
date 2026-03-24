package com.agent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentStoplineHardeningTest {

    private final AppConfig appConfig = new AppConfig();

    @Test
    void allowsAlignmentKnowledgeBaseOnlyDefaults() {
        AgentConfig config = new AgentConfig();

        assertDoesNotThrow(() -> appConfig.agentStoplineGuard(config));
    }

    @Test
    void rejectsEnabledTrue() {
        AgentConfig config = new AgentConfig();
        config.setEnabled(true);

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> appConfig.agentStoplineGuard(config));

        assertTrue(error.getMessage().contains("agent.enabled=true"));
    }

    @Test
    void rejectsStageBeyondAlignment() {
        AgentConfig config = new AgentConfig();
        config.setStage("execution");

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> appConfig.agentStoplineGuard(config));

        assertTrue(error.getMessage().contains("agent.stage=execution"));
    }

    @Test
    void rejectsNonKnowledgeBaseOnlyMode() {
        AgentConfig config = new AgentConfig();
        config.setMode("planner");

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> appConfig.agentStoplineGuard(config));

        assertTrue(error.getMessage().contains("agent.mode=planner"));
    }

    @Test
    void rejectsZeroInteractionAndOrchestrationFlags() {
        AgentConfig config = new AgentConfig();
        config.setZeroInteractionEnabled(true);
        config.setOrchestrationEnabled(true);

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> appConfig.agentStoplineGuard(config));

        assertTrue(error.getMessage().contains("agent.zero-interaction-enabled=true"));
        assertTrue(error.getMessage().contains("agent.orchestration-enabled=true"));
    }

    @Test
    void rejectsUnexpectedEntrypoint() {
        AgentConfig config = new AgentConfig();
        config.setEntrypoint("workflow-controller");

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> appConfig.agentStoplineGuard(config));

        assertTrue(error.getMessage().contains("agent.entrypoint=workflow-controller"));
    }
}
