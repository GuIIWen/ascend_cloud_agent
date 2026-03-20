package com.agent.knowledge.v2.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 场景元数据 - 包含TestScenario的元信息
 */
public class ScenarioMetadata {

    private String serviceName;
    private List<String> tags;
    private String createdBy;
    private long createdAt;
    private long updatedAt;
    private String version;

    public ScenarioMetadata() {
        this.tags = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.version = "1.0.0";
    }

    public ScenarioMetadata(Builder builder) {
        this.serviceName = builder.serviceName;
        this.tags = builder.tags;
        this.createdBy = builder.createdBy;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.version = builder.version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serviceName;
        private List<String> tags = new ArrayList<>();
        private String createdBy;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = System.currentTimeMillis();
        private String version = "1.0.0";

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder addTag(String tag) {
            this.tags.add(tag);
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder createdAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(long updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public ScenarioMetadata build() {
            return new ScenarioMetadata(this);
        }
    }

    // Getters and Setters
    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 更新时间戳
     */
    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScenarioMetadata that = (ScenarioMetadata) o;
        return createdAt == that.createdAt &&
                updatedAt == that.updatedAt &&
                Objects.equals(serviceName, that.serviceName) &&
                Objects.equals(createdBy, that.createdBy) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, createdBy, createdAt, updatedAt, version);
    }

    @Override
    public String toString() {
        return "ScenarioMetadata{" +
                "serviceName='" + serviceName + '\'' +
                ", tags=" + tags +
                ", createdBy='" + createdBy + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
