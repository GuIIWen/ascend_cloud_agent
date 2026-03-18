package com.agent.service;

import com.agent.config.KnowledgeBaseConfig;
import com.agent.crawler.WebDocumentCrawler;
import com.agent.model.ApiMetadata;
import com.agent.model.DocumentSource;
import com.agent.model.DocumentSourceType;
import com.agent.parser.JavaCodeParser;
import com.agent.processor.DocumentProcessor;
import com.agent.storage.MetadataStore;
import com.agent.storage.VectorStoreAdapter;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库服务实现
 */
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);
    private final KnowledgeBaseConfig config;
    private final DocumentProcessor documentProcessor;
    private final WebDocumentCrawler webCrawler;
    private final VectorStoreAdapter vectorStore;
    private final MetadataStore metadataStore;
    private final EmbeddingModel embeddingModel;
    private final JavaCodeParser javaCodeParser;

    public KnowledgeBaseServiceImpl(
            KnowledgeBaseConfig config,
            DocumentProcessor documentProcessor,
            WebDocumentCrawler webCrawler,
            VectorStoreAdapter vectorStore,
            MetadataStore metadataStore,
            EmbeddingModel embeddingModel) {
        this.config = config;
        this.documentProcessor = documentProcessor;
        this.webCrawler = webCrawler;
        this.vectorStore = vectorStore;
        this.metadataStore = metadataStore;
        this.embeddingModel = embeddingModel;
        this.javaCodeParser = new JavaCodeParser();
    }

    @Override
    public IndexStats indexJavaProject(String projectPath) {
        logger.info("Starting indexing Java project: {}", projectPath);
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failureCount = 0;
        List<Document> documents = new ArrayList<>();

        try {
            List<Path> javaFiles = Files.walk(Paths.get(projectPath))
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            logger.info("Found {} Java files", javaFiles.size());

            for (Path file : javaFiles) {
                try {
                    List<ApiMetadata> metadataList = javaCodeParser.parse(file);

                    for (ApiMetadata metadata : metadataList) {
                        metadataStore.save(metadata);

                        String content = metadata.getDescription() + " " +
                                        metadata.getClassName() + "." + metadata.getMethodName();
                        Document doc = Document.from(content,
                                Document.Metadata.from("source", file.toString())
                                        .put("apiId", metadata.getApiId())
                                        .put("type", DocumentSourceType.INTERNAL_CODE.name()));
                        documents.add(doc);
                    }
                    successCount++;
                } catch (IOException | SQLException e) {
                    logger.error("Failed to process file: {}", file, e);
                    failureCount++;
                }
            }

            documentProcessor.processAndStoreAll(documents);
            logger.info("Indexing completed: {} success, {} failures", successCount, failureCount);

        } catch (IOException e) {
            logger.error("Failed to index Java project", e);
            throw new RuntimeException("Failed to index Java project", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        return new IndexStats(documents.size(), successCount, failureCount, duration);
    }

    @Override
    public IndexStats indexExternalDocs(List<DocumentSource> sources) {
        logger.info("Starting indexing external docs: {} sources", sources.size());
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failureCount = 0;
        int totalDocs = 0;

        for (DocumentSource source : sources) {
            if (!source.isEnabled()) {
                continue;
            }

            totalDocs++;
            try {
                if (source.getType() == DocumentSourceType.WEB_PAGE) {
                    Document doc = webCrawler.crawl(source.getLocation());
                    documentProcessor.processAndStore(doc);
                    successCount++;
                } else if (source.getType() == DocumentSourceType.MARKDOWN_FILE) {
                    String content = Files.readString(Paths.get(source.getLocation()));
                    Document doc = Document.from(content,
                            Document.Metadata.from("source", source.getLocation())
                                    .put("type", source.getType().name()));
                    documentProcessor.processAndStore(doc);
                    successCount++;
                }
            } catch (IOException e) {
                logger.error("Failed to index source: {}", source.getLocation(), e);
                failureCount++;
            }
        }

        logger.info("External docs indexing completed: {} success, {} failures", successCount, failureCount);
        long duration = System.currentTimeMillis() - startTime;
        return new IndexStats(totalDocs, successCount, failureCount, duration);
    }

    @Override
    public List<ApiMetadata> search(String query, int topK) {
        logger.debug("Searching for: {}", query);
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<EmbeddingMatch<TextSegment>> matches = vectorStore.search(queryEmbedding, topK);

        List<ApiMetadata> results = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            String apiId = segment.metadata("apiId");

            if (apiId != null) {
                try {
                    ApiMetadata metadata = metadataStore.findByApiId(apiId);
                    if (metadata != null) {
                        results.add(metadata);
                    }
                } catch (SQLException e) {
                    logger.error("Failed to load metadata for apiId: {}", apiId, e);
                }
            } else {
                ApiMetadata metadata = ApiMetadata.builder()
                        .description(segment.text())
                        .sourceLocation(segment.metadata("source"))
                        .build();
                results.add(metadata);
            }
        }

        logger.info("Found {} results for query", results.size());
        return results;
    }

    @Override
    public void updateIndex(List<String> changedFiles) {
        logger.info("Updating index for {} changed files", changedFiles.size());
        for (String filePath : changedFiles) {
            try {
                String content = Files.readString(Paths.get(filePath));
                Document doc = Document.from(content,
                        Document.Metadata.from("source", filePath));
                documentProcessor.processAndStore(doc);
            } catch (IOException e) {
                logger.error("Failed to update index for: {}", filePath, e);
                throw new RuntimeException("Failed to update index for: " + filePath, e);
            }
        }
    }
}
