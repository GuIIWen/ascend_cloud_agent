package com.agent.config;

import com.agent.service.LLMService;
import com.agent.service.RerankService;
import com.agent.service.impl.DisabledLLMService;
import com.agent.service.impl.DisabledRerankService;
import com.agent.service.impl.HttpChatCompletionsLLMService;
import com.agent.service.impl.HttpEmbeddingModel;
import com.agent.service.impl.HttpRerankService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigModelSelectionTest {

    private final AppConfig appConfig = new AppConfig();

    @Test
    void usesLocalEmbeddingModelByDefault() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig();

        EmbeddingModel model = appConfig.embeddingModel(config);

        assertInstanceOf(AllMiniLmL6V2EmbeddingModel.class, model);
    }

    @Test
    void usesHttpEmbeddingModelForCustomProvider() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig();
        config.getEmbedding().setProvider("custom");
        config.getEmbedding().setApiUrl("http://127.0.0.1:9000/v1/embeddings");

        EmbeddingModel model = appConfig.embeddingModel(config);

        assertInstanceOf(HttpEmbeddingModel.class, model);
    }

    @Test
    void usesDisabledLlmByDefault() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig();

        LLMService llmService = appConfig.llmService(config);

        assertInstanceOf(DisabledLLMService.class, llmService);
        assertThrows(IllegalStateException.class, () -> llmService.generateTestCode("hello"));
    }

    @Test
    void usesHttpLlmForCustomProvider() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig();
        config.getLlm().setProvider("custom");
        config.getLlm().setApiUrl("http://127.0.0.1:9000/v1/chat/completions");

        LLMService llmService = appConfig.llmService(config);

        assertInstanceOf(HttpChatCompletionsLLMService.class, llmService);
    }

    @Test
    void usesDisabledRerankByDefault() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig();

        RerankService rerankService = appConfig.rerankService(config);

        assertInstanceOf(DisabledRerankService.class, rerankService);
    }

    @Test
    void usesHttpRerankForCustomProvider() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig();
        config.getRerank().setProvider("custom");
        config.getRerank().setApiUrl("http://127.0.0.1:9000/v1/rerank");

        RerankService rerankService = appConfig.rerankService(config);

        assertInstanceOf(HttpRerankService.class, rerankService);
    }
}
