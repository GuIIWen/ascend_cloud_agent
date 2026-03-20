package com.agent.config;

import com.agent.crawler.WebDocumentCrawler;
import com.agent.parser.HuaweiCloudApiParser;
import com.agent.processor.DocumentProcessor;
import com.agent.service.HuaweiCloudApiCrawlerService;
import com.agent.service.KnowledgeBaseService;
import com.agent.service.KnowledgeBaseServiceImpl;
import com.agent.storage.MetadataStore;
import com.agent.storage.VectorStoreAdapter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Spring配置 - 注入知识库组件
 */
@Configuration
public class AppConfig {

    @Bean
    public KnowledgeBaseConfig knowledgeBaseConfig() {
        KnowledgeBaseConfig config = new KnowledgeBaseConfig();
        // 默认配置
        return config;
    }

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
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public DocumentProcessor documentProcessor(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        return new DocumentProcessor(embeddingModel, embeddingStore);
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
