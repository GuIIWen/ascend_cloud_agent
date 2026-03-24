package com.agent.service.testcase;

import java.util.Objects;

/**
 * 生成结果引用信息。
 */
public class TestcaseCitation {
    public static final String TYPE_KNOWLEDGE_BASE = "knowledge-base";
    public static final String TYPE_REFERENCE_URL = "reference-url";

    private final String type;
    private final String apiId;
    private final String source;

    public TestcaseCitation(String type, String apiId, String source) {
        this.type = type;
        this.apiId = apiId;
        this.source = source;
    }

    public static TestcaseCitation knowledgeBase(String apiId, String source) {
        return new TestcaseCitation(TYPE_KNOWLEDGE_BASE, apiId, source);
    }

    public static TestcaseCitation referenceUrl(String source) {
        return new TestcaseCitation(TYPE_REFERENCE_URL, null, source);
    }

    public String getType() {
        return type;
    }

    public String getApiId() {
        return apiId;
    }

    public String getSource() {
        return source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TestcaseCitation that = (TestcaseCitation) o;
        return Objects.equals(type, that.type)
                && Objects.equals(apiId, that.apiId)
                && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, apiId, source);
    }
}
