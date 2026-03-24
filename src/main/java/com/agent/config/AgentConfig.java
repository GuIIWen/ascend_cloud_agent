package com.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Agent runtime status contract.
 *
 * <p>The main agent flow is not implemented yet. These properties make the
 * current alignment stage explicit in config and runtime status endpoints.
 */
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {

    static final String ALLOWED_STAGE = "alignment";
    static final String ALLOWED_MODE = "knowledge-base-only";
    static final String ALLOWED_ENTRYPOINT = "knowledge-base-controller";

    private boolean enabled = false;
    private String stage = ALLOWED_STAGE;
    private String mode = ALLOWED_MODE;
    private String entrypoint = ALLOWED_ENTRYPOINT;
    private boolean zeroInteractionEnabled = false;
    private boolean orchestrationEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public void setEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
    }

    public boolean isZeroInteractionEnabled() {
        return zeroInteractionEnabled;
    }

    public void setZeroInteractionEnabled(boolean zeroInteractionEnabled) {
        this.zeroInteractionEnabled = zeroInteractionEnabled;
    }

    public boolean isOrchestrationEnabled() {
        return orchestrationEnabled;
    }

    public void setOrchestrationEnabled(boolean orchestrationEnabled) {
        this.orchestrationEnabled = orchestrationEnabled;
    }

    public String effectiveStage() {
        return normalizeString(stage, ALLOWED_STAGE);
    }

    public String effectiveMode() {
        return normalizeString(mode, ALLOWED_MODE);
    }

    public String effectiveEntrypoint() {
        return normalizeString(entrypoint, ALLOWED_ENTRYPOINT);
    }

    public void validateAlignmentStopline() {
        List<String> violations = new ArrayList<>();

        if (enabled) {
            violations.add("agent.enabled=true");
        }
        if (zeroInteractionEnabled) {
            violations.add("agent.zero-interaction-enabled=true");
        }
        if (orchestrationEnabled) {
            violations.add("agent.orchestration-enabled=true");
        }
        if (!ALLOWED_STAGE.equalsIgnoreCase(effectiveStage())) {
            violations.add("agent.stage=" + effectiveStage());
        }
        if (!ALLOWED_MODE.equalsIgnoreCase(effectiveMode())) {
            violations.add("agent.mode=" + effectiveMode());
        }
        if (!ALLOWED_ENTRYPOINT.equalsIgnoreCase(effectiveEntrypoint())) {
            violations.add("agent.entrypoint=" + effectiveEntrypoint());
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Alignment stopline violation: current build only allows "
                            + "agent.enabled=false, "
                            + "agent.stage=" + ALLOWED_STAGE + ", "
                            + "agent.mode=" + ALLOWED_MODE + ", "
                            + "agent.entrypoint=" + ALLOWED_ENTRYPOINT + ", "
                            + "agent.zero-interaction-enabled=false, "
                            + "agent.orchestration-enabled=false. "
                            + "Violations: " + String.join(", ", violations));
        }
    }

    private String normalizeString(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        String normalized = value.trim();
        if (defaultValue.equalsIgnoreCase(normalized)) {
            return defaultValue;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
