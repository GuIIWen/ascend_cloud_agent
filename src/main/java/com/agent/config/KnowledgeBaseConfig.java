package com.agent.config;

import com.agent.model.DocumentSource;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库配置
 */
@ConfigurationProperties(prefix = "knowledge-base")
public class KnowledgeBaseConfig {
    private VectorStoreConfig vectorStore = new VectorStoreConfig();
    private EmbeddingConfig embedding = new EmbeddingConfig();
    private RerankConfig rerank = new RerankConfig();
    private LlmConfig llm = new LlmConfig();
    private List<DocumentSource> sources = new ArrayList<>();

    public static class VectorStoreConfig {
        private String type = "chroma";
        private String url = "http://localhost:22333";
        private String collection = "api-knowledge-base";

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
    }

    public static class EmbeddingConfig {
        // default to local model to keep dev startup usable without external dependencies
        private String provider = "local";
        private String apiUrl;
        private String apiKey;
        private String model = "bge-large-zh-v1.5";
        private int dimension = 1024;
        private int timeoutSeconds = 30;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class RerankConfig {
        private String provider = "none";
        private String apiUrl;
        private String apiKey;
        private String model = "bge-reranker-large";
        private int topK = 5;
        private int timeoutSeconds = 30;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class LlmConfig {
        private String provider = "none";
        private String apiUrl;
        private String apiKey;
        private String model = "qwen-coder-plus";
        private double temperature = 0.2;
        private int maxTokens = 4096;
        private int timeoutSeconds = 30;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public VectorStoreConfig getVectorStore() { return vectorStore; }
    public void setVectorStore(VectorStoreConfig vectorStore) { this.vectorStore = vectorStore; }
    public EmbeddingConfig getEmbedding() { return embedding; }
    public void setEmbedding(EmbeddingConfig embedding) { this.embedding = embedding; }
    public RerankConfig getRerank() { return rerank; }
    public void setRerank(RerankConfig rerank) { this.rerank = rerank; }
    public LlmConfig getLlm() { return llm; }
    public void setLlm(LlmConfig llm) { this.llm = llm; }
    public List<DocumentSource> getSources() { return sources; }
    public void setSources(List<DocumentSource> sources) { this.sources = sources; }
}
