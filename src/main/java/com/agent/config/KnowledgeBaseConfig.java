package com.agent.config;

import com.agent.model.DocumentSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库配置
 */
public class KnowledgeBaseConfig {
    private VectorStoreConfig vectorStore = new VectorStoreConfig();
    private EmbeddingConfig embedding = new EmbeddingConfig();
    private RerankConfig rerank = new RerankConfig();
    private List<DocumentSource> sources = new ArrayList<>();

    public static class VectorStoreConfig {
        private String type = "chroma";
        private String url = "http://localhost:8000";
        private String collection = "api-knowledge-base";

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
    }

    public static class EmbeddingConfig {
        private String provider = "custom";
        private String apiUrl;
        private String apiKey;
        private String model = "bge-large-zh-v1.5";
        private int dimension = 1024;

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
    }

    public static class RerankConfig {
        private String provider = "custom";
        private String apiUrl;
        private String apiKey;
        private String model = "bge-reranker-large";
        private int topK = 5;

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
    }

    public VectorStoreConfig getVectorStore() { return vectorStore; }
    public void setVectorStore(VectorStoreConfig vectorStore) { this.vectorStore = vectorStore; }
    public EmbeddingConfig getEmbedding() { return embedding; }
    public void setEmbedding(EmbeddingConfig embedding) { this.embedding = embedding; }
    public RerankConfig getRerank() { return rerank; }
    public void setRerank(RerankConfig rerank) { this.rerank = rerank; }
    public List<DocumentSource> getSources() { return sources; }
    public void setSources(List<DocumentSource> sources) { this.sources = sources; }
}
