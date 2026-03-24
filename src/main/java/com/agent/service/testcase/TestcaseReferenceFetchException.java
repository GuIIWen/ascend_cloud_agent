package com.agent.service.testcase;

/**
 * referenceUrl抓取失败异常。
 */
public class TestcaseReferenceFetchException extends RuntimeException {

    public TestcaseReferenceFetchException(String message, Throwable cause) {
        super(message, cause);
    }

    public TestcaseReferenceFetchException(String message) {
        super(message);
    }
}
