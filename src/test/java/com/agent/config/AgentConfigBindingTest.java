package com.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConfigBindingTest {

    @Test
    void bindsAgentAlignmentProperties() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("agent.enabled", "true");
        values.put("agent.stage", "alignment");
        values.put("agent.mode", "knowledge-base-only");
        values.put("agent.entrypoint", "knowledge-base-controller");
        values.put("agent.zero-interaction-enabled", "false");
        values.put("agent.orchestration-enabled", "false");

        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        AgentConfig config = binder.bind("agent", Bindable.of(AgentConfig.class))
                .orElseThrow(() -> new IllegalStateException("agent binding failed"));

        assertTrue(config.isEnabled());
        assertEquals("alignment", config.getStage());
        assertEquals("knowledge-base-only", config.getMode());
        assertEquals("knowledge-base-controller", config.getEntrypoint());
        assertFalse(config.isZeroInteractionEnabled());
        assertFalse(config.isOrchestrationEnabled());
    }
}
