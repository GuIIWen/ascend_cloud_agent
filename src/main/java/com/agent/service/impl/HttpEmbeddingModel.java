package com.agent.service.impl;

import com.agent.config.KnowledgeBaseConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于兼容 embeddings API 的 HTTP EmbeddingModel。
 */
public class HttpEmbeddingModel implements EmbeddingModel {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final KnowledgeBaseConfig.EmbeddingConfig config;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public HttpEmbeddingModel(KnowledgeBaseConfig.EmbeddingConfig config) {
        this(config, defaultClient(config), new ObjectMapper());
    }

    HttpEmbeddingModel(
            KnowledgeBaseConfig.EmbeddingConfig config,
            OkHttpClient client,
            ObjectMapper mapper) {
        this.config = config;
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public Response<Embedding> embed(String text) {
        return Response.from(embedAll(List.of(TextSegment.from(text))).content().get(0));
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return Response.from(embedAll(List.of(textSegment)).content().get(0));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        if (textSegments == null || textSegments.isEmpty()) {
            return Response.from(List.of());
        }

        validateConfig();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("input", textSegments.stream().map(TextSegment::text).toList());

        Request.Builder builder = new Request.Builder()
                .url(config.getApiUrl())
                .post(createJsonBody(requestBody));

        if (hasText(config.getApiKey())) {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }

        try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("Embedding request failed: HTTP " + response.code() + " body=" + body);
            }

            Map<?, ?> parsed = mapper.readValue(body, Map.class);
            List<?> data = (List<?>) parsed.get("data");
            if (data == null || data.isEmpty()) {
                throw new RuntimeException("Embedding response missing data");
            }

            List<Embedding> embeddings = new ArrayList<>(data.size());
            for (Object item : data) {
                if (!(item instanceof Map<?, ?> entry)) {
                    throw new RuntimeException("Embedding response item is malformed");
                }
                Object vectorObj = entry.get("embedding");
                if (!(vectorObj instanceof List<?> values)) {
                    throw new RuntimeException("Embedding response missing embedding vector");
                }
                embeddings.add(Embedding.from(toFloatArray(values)));
            }
            return Response.from(embeddings);
        } catch (IOException e) {
            throw new RuntimeException("Embedding request failed", e);
        }
    }

    @Override
    public int dimension() {
        return config.getDimension();
    }

    private void validateConfig() {
        if (!hasText(config.getApiUrl())) {
            throw new IllegalStateException("knowledge-base.embedding.api-url must be configured for custom provider");
        }
        if (!hasText(config.getModel())) {
            throw new IllegalStateException("knowledge-base.embedding.model must be configured for custom provider");
        }
    }

    private RequestBody createJsonBody(Map<String, Object> payload) {
        try {
            return RequestBody.create(mapper.writeValueAsString(payload), JSON);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize embedding request", e);
        }
    }

    private static float[] toFloatArray(List<?> values) {
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = ((Number) values.get(i)).floatValue();
        }
        return vector;
    }

    private static OkHttpClient defaultClient(KnowledgeBaseConfig.EmbeddingConfig config) {
        long timeout = Math.max(1, config.getTimeoutSeconds());
        return new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
