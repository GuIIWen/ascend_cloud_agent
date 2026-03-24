package com.agent.storage;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 向量存储适配器 - 支持Chroma和Milvus切换
 */
public class VectorStoreAdapter {
    private static final Logger logger = LoggerFactory.getLogger(VectorStoreAdapter.class);

    private final EmbeddingStore<TextSegment> store;

    public VectorStoreAdapter(EmbeddingStore<TextSegment> store) {
        this.store = store;
    }

    /**
     * 添加向量
     */
    public void add(Embedding embedding, TextSegment segment) {
        try {
            store.add(embedding, segment);
        } catch (Exception e) {
            logger.error("Failed to add embedding for source={}", segment != null ? segment.metadata("source") : "unknown", e);
        }
    }

    /**
     * 批量添加
     */
    public void addAll(List<Embedding> embeddings, List<TextSegment> segments) {
        try {
            store.addAll(embeddings, segments);
        } catch (Exception e) {
            logger.error("Failed to add {} embeddings to vector store", segments != null ? segments.size() : 0, e);
        }
    }

    /**
     * 搜索相似向量
     */
    public List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int maxResults) {
        try {
            return store.findRelevant(queryEmbedding, maxResults);
        } catch (Exception e) {
            logger.error("Vector store search failed for maxResults={}", maxResults, e);
            return List.of();
        }
    }
}
