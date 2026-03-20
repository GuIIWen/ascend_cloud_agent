package com.agent.knowledge.v2.exception;

/**
 * 场景未找到异常
 */
public class ScenarioNotFoundException extends RuntimeException {

    private final String scenarioId;

    public ScenarioNotFoundException(String scenarioId) {
        super("TestScenario not found with id: " + scenarioId);
        this.scenarioId = scenarioId;
    }

    public ScenarioNotFoundException(String scenarioId, String message) {
        super(message);
        this.scenarioId = scenarioId;
    }

    public ScenarioNotFoundException(String scenarioId, Throwable cause) {
        super("TestScenario not found with id: " + scenarioId, cause);
        this.scenarioId = scenarioId;
    }

    public String getScenarioId() {
        return scenarioId;
    }
}
