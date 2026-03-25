package com.agent.service.testcase;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用例生成结果。
 */
public class TestcaseGenerationResult {
    private final String javaTestCode;
    private final List<TestcaseCitation> citations;
    private final boolean degraded;
    private final String refinedRequirement;

    public TestcaseGenerationResult(String javaTestCode, List<TestcaseCitation> citations, boolean degraded) {
        this(javaTestCode, citations, degraded, null);
    }

    public TestcaseGenerationResult(
            String javaTestCode,
            List<TestcaseCitation> citations,
            boolean degraded,
            String refinedRequirement) {
        this.javaTestCode = javaTestCode;
        this.citations = citations == null ? List.of() : List.copyOf(citations);
        this.degraded = degraded;
        this.refinedRequirement = refinedRequirement;
    }

    public String getJavaTestCode() {
        return javaTestCode;
    }

    public List<TestcaseCitation> getCitations() {
        return new ArrayList<>(citations);
    }

    public boolean isDegraded() {
        return degraded;
    }

    public String getRefinedRequirement() {
        return refinedRequirement;
    }
}
