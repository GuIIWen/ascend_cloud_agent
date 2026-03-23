package com.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeBaseConfigBindingTest {

    @Test
    void bindsEmbeddingAndLlmProperties() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("knowledge-base.vector-store.type", "chroma");
        values.put("knowledge-base.vector-store.url", "http://127.0.0.1:22333");
        values.put("knowledge-base.vector-store.collection", "kb-test");
        values.put("knowledge-base.embedding.provider", "custom");
        values.put("knowledge-base.embedding.api-url", "http://127.0.0.1:9000/v1/embeddings");
        values.put("knowledge-base.embedding.model", "bge-m3");
        values.put("knowledge-base.embedding.dimension", "2048");
        values.put("knowledge-base.embedding.timeout-seconds", "9");
        values.put("knowledge-base.llm.provider", "custom");
        values.put("knowledge-base.llm.api-url", "http://127.0.0.1:9000/v1/chat/completions");
        values.put("knowledge-base.llm.model", "qwen-plus");
        values.put("knowledge-base.llm.temperature", "0.4");
        values.put("knowledge-base.llm.max-tokens", "512");
        values.put("knowledge-base.llm.timeout-seconds", "11");

        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        KnowledgeBaseConfig config = binder.bind("knowledge-base", Bindable.of(KnowledgeBaseConfig.class))
                .orElseThrow(() -> new IllegalStateException("knowledge-base binding failed"));

        assertEquals("chroma", config.getVectorStore().getType());
        assertEquals("http://127.0.0.1:22333", config.getVectorStore().getUrl());
        assertEquals("kb-test", config.getVectorStore().getCollection());
        assertEquals("custom", config.getEmbedding().getProvider());
        assertEquals("http://127.0.0.1:9000/v1/embeddings", config.getEmbedding().getApiUrl());
        assertEquals("bge-m3", config.getEmbedding().getModel());
        assertEquals(2048, config.getEmbedding().getDimension());
        assertEquals(9, config.getEmbedding().getTimeoutSeconds());
        assertEquals("custom", config.getLlm().getProvider());
        assertEquals("http://127.0.0.1:9000/v1/chat/completions", config.getLlm().getApiUrl());
        assertEquals("qwen-plus", config.getLlm().getModel());
        assertEquals(0.4, config.getLlm().getTemperature());
        assertEquals(512, config.getLlm().getMaxTokens());
        assertEquals(11, config.getLlm().getTimeoutSeconds());
    }
}
