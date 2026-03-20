package com.agent.knowledge.v2.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 测试场景 - 代表一个完整的测试操作序列
 */
public class TestScenario {

    private String scenarioId;
    private String name;
    private String description;
    private List<ScenarioStep> steps;
    private List<Validation> validations;
    private ScenarioMetadata metadata;

    public TestScenario() {
        this.steps = new ArrayList<>();
        this.validations = new ArrayList<>();
    }

    public TestScenario(Builder builder) {
        this.scenarioId = builder.scenarioId;
        this.name = builder.name;
        this.description = builder.description;
        this.steps = builder.steps != null
            ? builder.steps.stream().map(ScenarioStep::new).collect(java.util.stream.Collectors.toList())
            : new ArrayList<>();
        this.validations = builder.validations != null
            ? builder.validations.stream().map(Validation::new).collect(java.util.stream.Collectors.toList())
            : new ArrayList<>();
        this.metadata = builder.metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String scenarioId;
        private String name;
        private String description;
        private List<ScenarioStep> steps = new ArrayList<>();
        private List<Validation> validations = new ArrayList<>();
        private ScenarioMetadata metadata;

        public Builder scenarioId(String scenarioId) {
            this.scenarioId = scenarioId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder steps(List<ScenarioStep> steps) {
            this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
            return this;
        }

        public Builder addStep(ScenarioStep step) {
            this.steps.add(step);
            return this;
        }

        public Builder validations(List<Validation> validations) {
            this.validations = validations != null ? new ArrayList<>(validations) : new ArrayList<>();
            return this;
        }

        public Builder addValidation(Validation validation) {
            this.validations.add(validation);
            return this;
        }

        public Builder metadata(ScenarioMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public TestScenario build() {
            return new TestScenario(this);
        }
    }

    // Getters and Setters
    public String getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ScenarioStep> getSteps() {
        return new ArrayList<>(steps);
    }

    public void setSteps(List<ScenarioStep> steps) {
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
    }

    public List<Validation> getValidations() {
        return new ArrayList<>(validations);
    }

    public void setValidations(List<Validation> validations) {
        this.validations = validations != null ? new ArrayList<>(validations) : new ArrayList<>();
    }

    public ScenarioMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ScenarioMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * 将场景转换为文本描述，用于向量化
     */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Scenario: ").append(name).append("\n");
        sb.append("Description: ").append(description).append("\n");
        sb.append("Steps:\n");
        for (ScenarioStep step : steps) {
            sb.append("  ").append(step.getStepOrder()).append(". ")
              .append(step.getDescription()).append("\n");
        }
        if (validations != null && !validations.isEmpty()) {
            sb.append("Validations:\n");
            for (Validation v : validations) {
                sb.append("  - ").append(v.getDescription()).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestScenario that = (TestScenario) o;
        return scenarioId != null && scenarioId.equals(that.scenarioId);
    }

    @Override
    public int hashCode() {
        return scenarioId != null ? scenarioId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "TestScenario{" +
                "scenarioId='" + scenarioId + '\'' +
                ", name='" + name + '\'' +
                ", stepCount=" + (steps != null ? steps.size() : 0) +
                '}';
    }
}
