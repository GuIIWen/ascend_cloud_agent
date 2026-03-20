package com.agent.knowledge.v2.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 场景步骤 - 代表TestScenario中的一个操作步骤
 */
public class ScenarioStep {

    private int stepOrder;
    private String stepId;
    private String apiId;
    private String description;
    private Map<String, Object> inputParams;
    private String paramExtractFromPrev;
    private Map<String, String> outputMapping;

    public ScenarioStep() {
        this.stepId = "step_" + UUID.randomUUID().toString().substring(0, 8);
        this.inputParams = new HashMap<>();
        this.outputMapping = new HashMap<>();
    }

    /**
     * 拷贝构造函数
     */
    public ScenarioStep(ScenarioStep other) {
        this.stepOrder = other.stepOrder;
        this.stepId = other.stepId;
        this.apiId = other.apiId;
        this.description = other.description;
        this.inputParams = other.inputParams != null ? new HashMap<>(other.inputParams) : new HashMap<>();
        this.paramExtractFromPrev = other.paramExtractFromPrev;
        this.outputMapping = other.outputMapping != null ? new HashMap<>(other.outputMapping) : new HashMap<>();
    }

    public ScenarioStep(Builder builder) {
        this.stepOrder = builder.stepOrder;
        this.stepId = builder.stepId;
        this.apiId = builder.apiId;
        this.description = builder.description;
        this.inputParams = builder.inputParams;
        this.paramExtractFromPrev = builder.paramExtractFromPrev;
        this.outputMapping = builder.outputMapping;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int stepOrder;
        private String stepId;
        private String apiId;
        private String description;
        private Map<String, Object> inputParams = new HashMap<>();
        private String paramExtractFromPrev;
        private Map<String, String> outputMapping = new HashMap<>();

        public Builder stepOrder(int stepOrder) {
            this.stepOrder = stepOrder;
            return this;
        }

        public Builder stepId(String stepId) {
            this.stepId = stepId;
            return this;
        }

        public Builder apiId(String apiId) {
            this.apiId = apiId;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputParams(Map<String, Object> inputParams) {
            this.inputParams = inputParams;
            return this;
        }

        public Builder addInputParam(String key, Object value) {
            this.inputParams.put(key, value);
            return this;
        }

        public Builder paramExtractFromPrev(String paramExtractFromPrev) {
            this.paramExtractFromPrev = paramExtractFromPrev;
            return this;
        }

        public Builder outputMapping(Map<String, String> outputMapping) {
            this.outputMapping = outputMapping;
            return this;
        }

        public Builder addOutputMapping(String key, String jsonPath) {
            this.outputMapping.put(key, jsonPath);
            return this;
        }

        public ScenarioStep build() {
            return new ScenarioStep(this);
        }
    }

    // Getters and Setters
    public int getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(int stepOrder) {
        this.stepOrder = stepOrder;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getInputParams() {
        return new HashMap<>(inputParams);
    }

    public void setInputParams(Map<String, Object> inputParams) {
        this.inputParams = inputParams != null ? new HashMap<>(inputParams) : new HashMap<>();
    }

    public String getParamExtractFromPrev() {
        return paramExtractFromPrev;
    }

    public void setParamExtractFromPrev(String paramExtractFromPrev) {
        this.paramExtractFromPrev = paramExtractFromPrev;
    }

    public Map<String, String> getOutputMapping() {
        return new HashMap<>(outputMapping);
    }

    public void setOutputMapping(Map<String, String> outputMapping) {
        this.outputMapping = outputMapping != null ? new HashMap<>(outputMapping) : new HashMap<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScenarioStep that = (ScenarioStep) o;
        return stepId != null && stepId.equals(that.stepId);
    }

    @Override
    public int hashCode() {
        return stepId != null ? stepId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ScenarioStep{" +
                "stepId='" + stepId + '\'' +
                ", stepOrder=" + stepOrder +
                ", apiId='" + apiId + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
