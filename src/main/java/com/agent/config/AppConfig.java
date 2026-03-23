package com.agent.config;

import com.agent.crawler.WebDocumentCrawler;
import com.agent.parser.HuaweiCloudApiParser;
import com.agent.processor.DocumentProcessor;
import com.agent.service.HuaweiCloudApiCrawlerService;
import com.agent.service.KnowledgeBaseService;
import com.agent.service.KnowledgeBaseServiceImpl;
import com.agent.service.LLMService;
import com.agent.service.RerankService;
import com.agent.service.impl.DisabledLLMService;
import com.agent.service.impl.DisabledRerankService;
import com.agent.service.impl.HttpChatCompletionsLLMService;
import com.agent.service.impl.HttpEmbeddingModel;
import com.agent.service.impl.HttpRerankService;
import com.agent.storage.MetadataStore;
import com.agent.storage.VectorStoreAdapter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring配置 - 注入知识库组件
 */
@Configuration
@EnableConfigurationProperties({KnowledgeBaseConfig.class, AgentConfig.class})
public class AppConfig {

    static final String DATA_DIR_PROPERTY = "ascend.agent.data-dir";
    static final String HOME_PROPERTY = "ascend.agent.home";
    static final String HOME_ENV = "ASCEND_AGENT_HOME";
    static final String DEFAULT_HOME_DIR = ".ascend_agent";
    static final String DEFAULT_DB_DIR = "db";

    @Bean
    public MetadataStore metadataStore() {
        Path dataPath = resolveDataDir();
        try {
            Files.createDirectories(dataPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create data directory: " + dataPath, e);
        }

        return new MetadataStore(dataPath.resolve("api_metadata.db").toString());
    }

    @Bean
    public InfoContributor agentInfoContributor(AgentConfig agentConfig) {
        return builder -> {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("enabled", agentConfig.isEnabled());
            details.put("stage", agentConfig.getStage());
            details.put("mode", agentConfig.getMode());
            details.put("entrypoint", agentConfig.getEntrypoint());
            details.put("zeroInteractionEnabled", agentConfig.isZeroInteractionEnabled());
            details.put("orchestrationEnabled", agentConfig.isOrchestrationEnabled());
            builder.withDetail("agent", details);
        };
    }

    Path resolveDataDir() {
        return resolveDataDir(
                System.getProperty(DATA_DIR_PROPERTY),
                System.getProperty(HOME_PROPERTY),
                System.getenv(HOME_ENV),
                System.getProperty("user.dir"));
    }

    Path resolveDataDir(String explicitDataDir, String configuredHome, String envHome, String userDir) {
        if (isBlank(explicitDataDir)) {
            return resolveAgentHome(configuredHome, envHome, userDir).resolve(DEFAULT_DB_DIR).normalize();
        }
        return Paths.get(explicitDataDir.trim()).normalize();
    }

    Path resolveAgentHome(String configuredHome, String envHome, String userDir) {
        if (!isBlank(configuredHome)) {
            return Paths.get(configuredHome.trim()).normalize();
        }
        if (!isBlank(envHome)) {
            return Paths.get(envHome.trim()).normalize();
        }
        return Paths.get(userDir).resolve(DEFAULT_HOME_DIR).normalize();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(KnowledgeBaseConfig config) {
        KnowledgeBaseConfig.VectorStoreConfig vectorStore =
                config.getVectorStore() != null ? config.getVectorStore() : new KnowledgeBaseConfig.VectorStoreConfig();
        String type = vectorStore.getType() != null ? vectorStore.getType().trim() : "";

        if (type.isEmpty() || "chroma".equalsIgnoreCase(type)) {
            return createChromaEmbeddingStore(vectorStore);
        }
        if ("milvus".equalsIgnoreCase(type)) {
            throw new IllegalStateException("knowledge-base.vector-store.type=milvus is not implemented yet");
        }
        throw new IllegalStateException("Unsupported knowledge-base.vector-store.type: " + type);
    }

    EmbeddingStore<TextSegment> createChromaEmbeddingStore(KnowledgeBaseConfig.VectorStoreConfig vectorStore) {
        String baseUrl = requireValue(vectorStore.getUrl(), "knowledge-base.vector-store.url");
        String collection = requireValue(vectorStore.getCollection(), "knowledge-base.vector-store.collection");

        return ChromaEmbeddingStore.builder()
                .baseUrl(baseUrl)
                .collectionName(collection)
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private String requireValue(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalStateException(name + " must not be blank");
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
    @Lazy
    public RerankService rerankService(KnowledgeBaseConfig config) {
        String provider = config.getRerank() != null ? config.getRerank().getProvider() : null;
        if (provider == null || provider.trim().isEmpty() || "none".equalsIgnoreCase(provider)) {
            return new DisabledRerankService();
        }
        if ("custom".equalsIgnoreCase(provider)) {
            return new HttpRerankService(config.getRerank());
        }
        throw new IllegalStateException("Unsupported knowledge-base.rerank.provider: " + provider);
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
