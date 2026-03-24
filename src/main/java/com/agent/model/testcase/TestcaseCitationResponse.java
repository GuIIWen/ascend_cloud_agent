package com.agent.model.testcase;

public class TestcaseCitationResponse {

    private String type;
    private String apiId;
    private String source;

    public TestcaseCitationResponse() {
    }

    public TestcaseCitationResponse(String type, String apiId, String source) {
        this.type = type;
        this.apiId = apiId;
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
