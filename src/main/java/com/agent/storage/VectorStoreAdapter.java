package com.agent.storage;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.List;

/**
 * 向量存储适配器 - 支持Chroma和Milvus切换
 */
public class VectorStoreAdapter {
    private final EmbeddingStore<TextSegment> store;

    public VectorStoreAdapter(EmbeddingStore<TextSegment> store) {
        this.store = store;
    }

    /**
     * 添加向量
     */
    public void add(Embedding embedding, TextSegment segment) {
        store.add(embedding, segment);
    }

    /**
     * 批量添加
     */
    public void addAll(List<Embedding> embeddings, List<TextSegment> segments) {
        store.addAll(embeddings, segments);
    }

    /**
     * 搜索相似向量
     */
    public List<EmbeddingMatch<TextSegment>> search(Embedding queryEmbedding, int maxResults) {
        return store.findRelevant(queryEmbedding, maxResults);
    }
}
