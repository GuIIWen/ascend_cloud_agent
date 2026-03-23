package com.agent.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigVectorStoreSelectionTest {

    @Test
    void usesChromaEmbeddingStoreByDefault() {
        RecordingAppConfig appConfig = new RecordingAppConfig();
        KnowledgeBaseConfig config = new KnowledgeBaseConfig();
        config.getVectorStore().setUrl("http://127.0.0.1:22333");
        config.getVectorStore().setCollection("api-knowledge-base");

        EmbeddingStore<TextSegment> store = appConfig.embeddingStore(config);

        assertSame(appConfig.storeToReturn, store);
        assertSame(config.getVectorStore(), appConfig.lastVectorStoreConfig);
        assertEquals("http://127.0.0.1:22333", appConfig.lastVectorStoreConfig.getUrl());
        assertEquals("api-knowledge-base", appConfig.lastVectorStoreConfig.getCollection());
    }

    @Test
    void rejectsMilvusUntilImplemented() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig();
        config.getVectorStore().setType("milvus");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new AppConfig().embeddingStore(config));

        assertEquals("knowledge-base.vector-store.type=milvus is not implemented yet", error.getMessage());
    }

    @Test
    void rejectsUnknownVectorStoreType() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig();
        config.getVectorStore().setType("redis");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new AppConfig().embeddingStore(config));

        assertEquals("Unsupported knowledge-base.vector-store.type: redis", error.getMessage());
    }

    private static final class RecordingAppConfig extends AppConfig {
        private final EmbeddingStore<TextSegment> storeToReturn = new NoopEmbeddingStore();
        private KnowledgeBaseConfig.VectorStoreConfig lastVectorStoreConfig;

        @Override
        EmbeddingStore<TextSegment> createChromaEmbeddingStore(KnowledgeBaseConfig.VectorStoreConfig vectorStore) {
            this.lastVectorStoreConfig = vectorStore;
            return storeToReturn;
        }
    }

    private static final class NoopEmbeddingStore implements EmbeddingStore<TextSegment> {
        @Override
        public String add(Embedding embedding) {
            return "id";
        }

        @Override
        public void add(String id, Embedding embedding) {
        }

        @Override
        public String add(Embedding embedding, TextSegment embedded) {
            return "id";
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            return List.of();
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
            return List.of();
        }

        @Override
        public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
            return List.of();
        }

        @Override
        public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
            return new EmbeddingSearchResult<>(List.of());
        }

        @Override
        public void removeAll(Collection<String> ids) {
        }
    }
}
