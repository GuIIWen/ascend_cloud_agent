package com.agent.service.impl;

import com.agent.config.KnowledgeBaseConfig;
import com.agent.service.LLMPromptMarkers;
import com.agent.service.LLMService;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 OpenAI Chat Completions 兼容协议的最小LLM实现。
 */
public class HttpChatCompletionsLLMService implements LLMService {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final Logger logger = LoggerFactory.getLogger(HttpChatCompletionsLLMService.class);
    private static final int REFINEMENT_MAX_TOKENS = 512;
    private static final int TESTCASE_GENERATION_MAX_TOKENS = 1536;

    private final KnowledgeBaseConfig.LlmConfig config;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public HttpChatCompletionsLLMService(KnowledgeBaseConfig.LlmConfig config) {
        this(config, defaultClient(config), new ObjectMapper());
    }

    HttpChatCompletionsLLMService(
            KnowledgeBaseConfig.LlmConfig config,
            OkHttpClient client,
            ObjectMapper mapper) {
        this.config = config;
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public String generateTestCode(String prompt) {
        validateConfig();
        String promptMode = detectPromptMode(prompt);
        String requestPrompt = stripPromptMarkers(prompt);
        int maxTokens = resolveMaxTokens(promptMode);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("temperature", config.getTemperature());
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("messages", List.of(Map.of("role", "user", "content", requestPrompt)));

        Request.Builder builder = new Request.Builder()
                .url(config.getApiUrl())
                .post(createJsonBody(requestBody));

        if (hasText(config.getApiKey())) {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }

        long startNanos = System.nanoTime();
        try (Response response = client.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            logger.info("LLM request completed: mode={} status={} maxTokens={} elapsedMs={}",
                    promptMode, response.code(), maxTokens, elapsedMillis(startNanos));
            if (!response.isSuccessful()) {
                throw new RuntimeException("LLM request failed: HTTP " + response.code() + " body=" + body);
            }

            Map<?, ?> parsed = mapper.readValue(body, Map.class);
            List<?> choices = (List<?>) parsed.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("LLM response missing choices");
            }

            Object first = choices.get(0);
            if (!(first instanceof Map<?, ?> firstChoice)) {
                throw new RuntimeException("LLM response choice is malformed");
            }

            Object messageObj = firstChoice.get("message");
            if (!(messageObj instanceof Map<?, ?> message)) {
                throw new RuntimeException("LLM response missing message");
            }

            Object content = message.get("content");
            if (!(content instanceof String text)) {
                throw new RuntimeException("LLM response missing content");
            }

            return text;
        } catch (IOException e) {
            logger.warn("LLM request failed: mode={} maxTokens={} elapsedMs={}",
                    promptMode, maxTokens, elapsedMillis(startNanos), e);
            throw new RuntimeException("LLM request failed", e);
        }
    }

    private void validateConfig() {
        if (!hasText(config.getApiUrl())) {
            throw new IllegalStateException("knowledge-base.llm.api-url must be configured for custom provider");
        }
        if (!hasText(config.getModel())) {
            throw new IllegalStateException("knowledge-base.llm.model must be configured for custom provider");
        }
    }

    private RequestBody createJsonBody(Map<String, Object> payload) {
        try {
            return RequestBody.create(mapper.writeValueAsString(payload), JSON);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize LLM request", e);
        }
    }

    private static OkHttpClient defaultClient(KnowledgeBaseConfig.LlmConfig config) {
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

    private String detectPromptMode(String prompt) {
        if (prompt == null) {
            return "default";
        }
        if (prompt.startsWith(LLMPromptMarkers.REQUIREMENT_REFINEMENT)) {
            return LLMPromptMarkers.REQUIREMENT_REFINEMENT;
        }
        if (prompt.startsWith(LLMPromptMarkers.TESTCASE_GENERATION)) {
            return LLMPromptMarkers.TESTCASE_GENERATION;
        }
        return "default";
    }

    private String stripPromptMarkers(String prompt) {
        if (!hasText(prompt)) {
            return prompt;
        }
        return prompt
                .replaceFirst("^" + java.util.regex.Pattern.quote(LLMPromptMarkers.REQUIREMENT_REFINEMENT) + "\\s*", "")
                .replaceFirst("^" + java.util.regex.Pattern.quote(LLMPromptMarkers.TESTCASE_GENERATION) + "\\s*", "");
    }

    private int resolveMaxTokens(String promptMode) {
        int configured = Math.max(1, config.getMaxTokens());
        if (LLMPromptMarkers.REQUIREMENT_REFINEMENT.equals(promptMode)) {
            return Math.min(configured, REFINEMENT_MAX_TOKENS);
        }
        if (LLMPromptMarkers.TESTCASE_GENERATION.equals(promptMode)) {
            return Math.min(configured, TESTCASE_GENERATION_MAX_TOKENS);
        }
        return configured;
    }

    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
