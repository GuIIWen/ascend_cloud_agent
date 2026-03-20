package com.agent.config;

import com.agent.crawler.WebDocumentCrawler;
import com.agent.parser.HuaweiCloudApiParser;
import com.agent.processor.DocumentProcessor;
import com.agent.service.HuaweiCloudApiCrawlerService;
import com.agent.service.KnowledgeBaseService;
import com.agent.service.KnowledgeBaseServiceImpl;
import com.agent.service.LLMService;
import com.agent.service.impl.DisabledLLMService;
import com.agent.service.impl.HttpChatCompletionsLLMService;
import com.agent.service.impl.HttpEmbeddingModel;
import com.agent.storage.MetadataStore;
import com.agent.storage.VectorStoreAdapter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Spring配置 - 注入知识库组件
 */
@Configuration
@EnableConfigurationProperties(KnowledgeBaseConfig.class)
public class AppConfig {

    @Bean
    public MetadataStore metadataStore() {
        String dataDir = System.getProperty(
                "ascend.agent.data-dir",
                Paths.get(System.getProperty("user.dir"), "data").toString());
        Path dataPath = Paths.get(dataDir);
        try {
            Files.createDirectories(dataPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create data directory: " + dataPath, e);
        }

        return new MetadataStore(dataPath.resolve("api_metadata.db").toString());
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // 使用内存EmbeddingStore（演示用）
        // 生产环境应使用Chroma或Milvus
        return new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>();
    }

    @Bean
    @Lazy
    public EmbeddingModel embeddingModel(KnowledgeBaseConfig config) {
        String provider = config.getEmbedding() != null ? config.getEmbedding().getProvider() : null;
        if (provider == null || provider.trim().isEmpty() || "local".equalsIgnoreCase(provider)) {
            return new AllMiniLmL6V2EmbeddingModel();
        }
        if ("custom".equalsIgnoreCase(provider)) {
            return new HttpEmbeddingModel(config.getEmbedding());
        }
        throw new IllegalStateException("Unsupported knowledge-base.embedding.provider: " + provider);
    }

    @Bean
    public DocumentProcessor documentProcessor(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        return new DocumentProcessor(embeddingModel, embeddingStore);
    }

    @Bean
    @Lazy
    public LLMService llmService(KnowledgeBaseConfig config) {
        String provider = config.getLlm() != null ? config.getLlm().getProvider() : null;
        if (provider == null || provider.trim().isEmpty() || "none".equalsIgnoreCase(provider)) {
            return new DisabledLLMService();
        }
        if ("custom".equalsIgnoreCase(provider)) {
            return new HttpChatCompletionsLLMService(config.getLlm());
        }
        throw new IllegalStateException("Unsupported knowledge-base.llm.provider: " + provider);
    }

    @Bean
    public WebDocumentCrawler webDocumentCrawler() {
        return new WebDocumentCrawler();
    }

    @Bean
    public HuaweiCloudApiParser huaweiCloudApiParser() {
        return new HuaweiCloudApiParser();
    }

    @Bean
    public HuaweiCloudApiCrawlerService huaweiCloudApiCrawlerService(
            WebDocumentCrawler webDocumentCrawler,
            HuaweiCloudApiParser huaweiCloudApiParser,
            MetadataStore metadataStore,
            DocumentProcessor documentProcessor) {
        return new HuaweiCloudApiCrawlerService(
                webDocumentCrawler,
                huaweiCloudApiParser,
                metadataStore,
                documentProcessor);
    }

    @Bean
    public VectorStoreAdapter vectorStoreAdapter(EmbeddingStore<TextSegment> embeddingStore) {
        return new VectorStoreAdapter(embeddingStore);
    }

    @Bean
    public KnowledgeBaseService knowledgeBaseService(
            KnowledgeBaseConfig config,
            DocumentProcessor documentProcessor,
            WebDocumentCrawler webCrawler,
            VectorStoreAdapter vectorStoreAdapter,
            MetadataStore metadataStore,
            EmbeddingModel embeddingModel) {
        return new KnowledgeBaseServiceImpl(
                config,
                documentProcessor,
                webCrawler,
                vectorStoreAdapter,
                metadataStore,
                embeddingModel);
    }
}
