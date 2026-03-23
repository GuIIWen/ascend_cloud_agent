package com.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent runtime status contract.
 *
 * <p>The main agent flow is not implemented yet. These properties make the
 * current alignment stage explicit in config and runtime status endpoints.
 */
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {

    private boolean enabled = false;
    private String stage = "alignment";
    private String mode = "knowledge-base-only";
    private String entrypoint = "knowledge-base-controller";
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
}
