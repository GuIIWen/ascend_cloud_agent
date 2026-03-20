package com.agent.config;

/**
 * 模型配置
 */
public class ModelConfig {

    private EmbeddingConfig embedding;

    public ModelConfig() {
        this.embedding = new EmbeddingConfig();
    }

    public EmbeddingConfig getEmbedding() {
        return embedding;
    }

    public void setEmbedding(EmbeddingConfig embedding) {
        this.embedding = embedding;
    }

    /**
     * Embedding配置
     */
    public static class EmbeddingConfig {
        private String provider = "custom";
        private String apiUrl;
        private String apiKey;
        private String modelName = "bge-large-zh-v1.5";
        private int dimension = 1024;
        private int timeout = 30;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }
        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
    }
}
