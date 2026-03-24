package com.agent.service.testcase;

/**
 * 当知识库未命中且缺少referenceUrl时抛出的硬错误。
 */
public class TestcaseReferenceUrlRequiredException extends RuntimeException {
    public static final String ERROR_CODE = "TESTCASE_REFERENCE_URL_REQUIRED";

    public TestcaseReferenceUrlRequiredException() {
        super("No related API found in knowledge base. Please provide referenceUrl to generate Java testcase code.");
    }
}
