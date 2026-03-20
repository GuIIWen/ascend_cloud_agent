package com.agent.service.impl;

import com.agent.config.KnowledgeBaseConfig;
import com.agent.service.RerankService;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 `/v1/rerank` 兼容协议的最小 HTTP Rerank 实现。
 */
public class HttpRerankService implements RerankService {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final KnowledgeBaseConfig.RerankConfig config;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public HttpRerankService(KnowledgeBaseConfig.RerankConfig config) {
        this(config, defaultClient(config), new ObjectMapper());
    }

    HttpRerankService(
            KnowledgeBaseConfig.RerankConfig config,
            OkHttpClient client,
            ObjectMapper mapper) {
        this.config = config;
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        validateConfig();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("query", query);
        requestBody.put("documents", candidates);
        requestBody.put("top_n", Math.min(Math.max(1, config.getTopK()), candidates.size()));
        if (hasText(config.getModel())) {
            requestBody.put("model", config.getModel());
        }

        Request.Builder builder = new Request.Builder()
                .url(config.getApiUrl())
                .post(createJsonBody(requestBody));

        if (hasText(config.getApiKey())) {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("Rerank request failed: HTTP " + response.code() + " body=" + body);
            }

            Map<?, ?> parsed = mapper.readValue(body, Map.class);
            List<?> results = (List<?>) parsed.get("results");
            if (results == null) {
                throw new RuntimeException("Rerank response missing results");
            }

            List<RerankResult> reranked = new ArrayList<>(results.size());
            for (Object item : results) {
                if (!(item instanceof Map<?, ?> entry)) {
                    throw new RuntimeException("Rerank response item is malformed");
                }
                Object index = entry.get("index");
                Object score = entry.get("relevance_score");
                if (!(index instanceof Number) || !(score instanceof Number)) {
                    throw new RuntimeException("Rerank response missing index or relevance_score");
                }
                reranked.add(new RerankResult(((Number) index).intValue(), ((Number) score).doubleValue()));
            }
            return reranked;
        } catch (IOException e) {
            throw new RuntimeException("Rerank request failed", e);
        }
    }

    private void validateConfig() {
        if (!hasText(config.getApiUrl())) {
            throw new IllegalStateException("knowledge-base.rerank.api-url must be configured for custom provider");
        }
    }

    private RequestBody createJsonBody(Map<String, Object> payload) {
        try {
            return RequestBody.create(mapper.writeValueAsString(payload), JSON);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize rerank request", e);
        }
    }

    private static OkHttpClient defaultClient(KnowledgeBaseConfig.RerankConfig config) {
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
