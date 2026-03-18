package com.agent.service.impl;

import com.agent.service.EmbeddingService;
import com.agent.config.ModelConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 自定义Embedding服务实现
 */
public class CustomEmbeddingService implements EmbeddingService {

    private final OkHttpClient client;
    private final ModelConfig.EmbeddingConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public CustomEmbeddingService(ModelConfig.EmbeddingConfig config) {
        this.config = config;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout(), java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    @Override
    public float[] embed(String text) {
        try {
            Map<String, Object> requestBody = Map.of(
                "model", config.getModelName(),
                "input", text
            );

            Request request = new Request.Builder()
                .url(config.getApiUrl())
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(RequestBody.create(
                    mapper.writeValueAsString(requestBody),
                    MediaType.parse("application/json")
                ))
                .build();

            try (Response response = client.newCall(request).execute()) {
                Map<String, Object> result = mapper.readValue(response.body().string(), Map.class);
                List<Double> embedding = (List<Double>) ((Map) ((List) result.get("data")).get(0)).get("embedding");
                return embedding.stream().map(Double::floatValue).collect(Collectors.toList())
                    .stream().mapToDouble(Float::doubleValue).collect(Collectors.toList())
                    .stream().map(Double::floatValue).toArray(float[]::new);
            }
        } catch (Exception e) {
            throw new RuntimeException("Embedding failed", e);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).collect(Collectors.toList());
    }
}
