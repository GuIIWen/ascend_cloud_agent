package com.agent.model;

import java.util.ArrayList;
import java.util.List;

/**
 * API元数据
 */
public class ApiMetadata {
    private String apiId;
    private String className;
    private String methodName;
    private String signature;
    private String description;

    private DocumentSourceType sourceType;
    private String sourceLocation;

    private List<Parameter> parameters = new ArrayList<>();
    private String returnType;
    private List<String> exceptions = new ArrayList<>();

    // 外部API字段
    private String httpMethod;
    private String endpoint;
    private String requestBody;
    private String responseBody;

    private float[] vector;

    private ApiMetadata(Builder builder) {
        this.apiId = builder.apiId;
        this.className = builder.className;
        this.methodName = builder.methodName;
        this.signature = builder.signature;
        this.description = builder.description;
        this.sourceType = builder.sourceType;
        this.sourceLocation = builder.sourceLocation;
        this.parameters = builder.parameters;
        this.returnType = builder.returnType;
        this.exceptions = builder.exceptions;
        this.httpMethod = builder.httpMethod;
        this.endpoint = builder.endpoint;
        this.requestBody = builder.requestBody;
        this.responseBody = builder.responseBody;
        this.vector = builder.vector;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String apiId;
        private String className;
        private String methodName;
        private String signature;
        private String description;
        private DocumentSourceType sourceType;
        private String sourceLocation;
        private List<Parameter> parameters = new ArrayList<>();
        private String returnType;
        private List<String> exceptions = new ArrayList<>();
        private String httpMethod;
        private String endpoint;
        private String requestBody;
        private String responseBody;
        private float[] vector;

        public Builder apiId(String apiId) { this.apiId = apiId; return this; }
        public Builder className(String className) { this.className = className; return this; }
        public Builder methodName(String methodName) { this.methodName = methodName; return this; }
        public Builder signature(String signature) { this.signature = signature; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder sourceType(DocumentSourceType sourceType) { this.sourceType = sourceType; return this; }
        public Builder sourceLocation(String sourceLocation) { this.sourceLocation = sourceLocation; return this; }
        public Builder parameters(List<Parameter> parameters) { this.parameters = parameters; return this; }
        public Builder returnType(String returnType) { this.returnType = returnType; return this; }
        public Builder exceptions(List<String> exceptions) { this.exceptions = exceptions; return this; }
        public Builder httpMethod(String httpMethod) { this.httpMethod = httpMethod; return this; }
        public Builder endpoint(String endpoint) { this.endpoint = endpoint; return this; }
        public Builder requestBody(String requestBody) { this.requestBody = requestBody; return this; }
        public Builder responseBody(String responseBody) { this.responseBody = responseBody; return this; }
        public Builder vector(float[] vector) { this.vector = vector; return this; }

        public ApiMetadata build() {
            return new ApiMetadata(this);
        }
    }

    // Getters
    public String getApiId() { return apiId; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getSignature() { return signature; }
    public String getDescription() { return description; }
    public DocumentSourceType getSourceType() { return sourceType; }
    public String getSourceLocation() { return sourceLocation; }
    public List<Parameter> getParameters() { return new ArrayList<>(parameters); }
    public String getReturnType() { return returnType; }
    public List<String> getExceptions() { return new ArrayList<>(exceptions); }
    public String getHttpMethod() { return httpMethod; }
    public String getEndpoint() { return endpoint; }
    public String getRequestBody() { return requestBody; }
    public String getResponseBody() { return responseBody; }
    public float[] getVector() { return vector; }

    // Setter
    public void setVector(float[] vector) { this.vector = vector; }

    @Override
    public String toString() {
        return "ApiMetadata{" +
                "apiId='" + apiId + '\'' +
                ", className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                ", signature='" + signature + '\'' +
                ", sourceType=" + sourceType +
                ", sourceLocation='" + sourceLocation + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiMetadata that = (ApiMetadata) o;
        return apiId != null && apiId.equals(that.apiId);
    }

    @Override
    public int hashCode() {
        return apiId != null ? apiId.hashCode() : 0;
    }
}
