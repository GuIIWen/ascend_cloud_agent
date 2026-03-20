package com.agent.service.impl;

import com.agent.config.KnowledgeBaseConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpModelServiceTest {

    @Test
    void embeddingModelCallsConfiguredEndpoint() {
        StringBuilder observedBody = new StringBuilder();
        OkHttpClient client = clientResponding(
                "{\"data\":[{\"embedding\":[0.1,0.2]},{\"embedding\":[0.3,0.4]}]}",
                observedBody);

        KnowledgeBaseConfig.EmbeddingConfig config = new KnowledgeBaseConfig.EmbeddingConfig();
        config.setProvider("custom");
        config.setApiUrl("http://embedding.test/v1/embeddings");
        config.setModel("bge-test");
        config.setDimension(2);

        HttpEmbeddingModel model = new HttpEmbeddingModel(config, client, new ObjectMapper());

        var response = model.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b")));

        assertEquals(2, response.content().size());
        assertArrayEquals(new float[]{0.1f, 0.2f}, response.content().get(0).vector());
        assertEquals(2, model.dimension());
        assertTrue(observedBody.toString().contains("\"model\":\"bge-test\""));
        assertTrue(observedBody.toString().contains("\"input\":[\"a\",\"b\"]"));
    }

    @Test
    void llmServiceCallsConfiguredEndpoint() {
        StringBuilder observedBody = new StringBuilder();
        OkHttpClient client = clientResponding(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"generated test\"}}]}",
                observedBody);

        KnowledgeBaseConfig.LlmConfig config = new KnowledgeBaseConfig.LlmConfig();
        config.setProvider("custom");
        config.setApiUrl("http://llm.test/v1/chat/completions");
        config.setModel("qwen-test");
        config.setMaxTokens(128);
        config.setTemperature(0.1);

        HttpChatCompletionsLLMService service =
                new HttpChatCompletionsLLMService(config, client, new ObjectMapper());

        String result = service.generateTestCode("write test");

        assertEquals("generated test", result);
        assertTrue(observedBody.toString().contains("\"model\":\"qwen-test\""));
        assertTrue(observedBody.toString().contains("\"max_tokens\":128"));
        assertTrue(observedBody.toString().contains("\"content\":\"write test\""));
    }

    private static OkHttpClient clientResponding(String responseBody, StringBuilder observedBody) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    observedBody.append(readBody(request));
                    return new Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(responseBody, MediaType.parse("application/json")))
                            .build();
                })
                .build();
    }

    private static String readBody(Request request) throws IOException {
        if (request.body() == null) {
            return "";
        }
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }
}
