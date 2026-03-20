package com.agent.knowledge.v2.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 验证点 - 代表TestScenario中的一个验证点
 */
public class Validation {

    private String validationId;
    private ValidationType type;
    private String target;
    private String expectedValue;
    private String actualValuePath;
    private String description;

    public Validation() {
        this.validationId = "val_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 拷贝构造函数
     */
    public Validation(Validation other) {
        this.validationId = other.validationId;
        this.type = other.type;
        this.target = other.target;
        this.expectedValue = other.expectedValue;
        this.actualValuePath = other.actualValuePath;
        this.description = other.description;
    }

    public Validation(Builder builder) {
        this.validationId = builder.validationId;
        this.type = builder.type;
        this.target = builder.target;
        this.expectedValue = builder.expectedValue;
        this.actualValuePath = builder.actualValuePath;
        this.description = builder.description;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String validationId;
        private ValidationType type;
        private String target;
        private String expectedValue;
        private String actualValuePath;
        private String description;

        public Builder validationId(String validationId) {
            this.validationId = validationId;
            return this;
        }

        public Builder type(ValidationType type) {
            this.type = type;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder expectedValue(String expectedValue) {
            this.expectedValue = expectedValue;
            return this;
        }

        public Builder actualValuePath(String actualValuePath) {
            this.actualValuePath = actualValuePath;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Validation build() {
            return new Validation(this);
        }
    }

    /**
     * 验证类型枚举
     */
    public enum ValidationType {
        /** 断言相等 */
        ASSERT_EQUAL,
        /** 断言非空 */
        ASSERT_NOT_NULL,
        /** 断言包含 */
        ASSERT_CONTAINS,
        /** 断言状态码 */
        ASSERT_STATUS,
        /** JSON路径验证 */
        ASSERT_JSON_PATH
    }

    // Getters and Setters
    public String getValidationId() {
        return validationId;
    }

    public void setValidationId(String validationId) {
        this.validationId = validationId;
    }

    public ValidationType getType() {
        return type;
    }

    public void setType(ValidationType type) {
        this.type = type;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    public String getActualValuePath() {
        return actualValuePath;
    }

    public void setActualValuePath(String actualValuePath) {
        this.actualValuePath = actualValuePath;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Validation that = (Validation) o;
        return validationId != null && validationId.equals(that.validationId);
    }

    @Override
    public int hashCode() {
        return validationId != null ? validationId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Validation{" +
                "validationId='" + validationId + '\'' +
                ", type=" + type +
                ", target='" + target + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
