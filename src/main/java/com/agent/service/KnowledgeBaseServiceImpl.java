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
import dev.langchain4j.data.document.Metadata;
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
        int totalDocuments = 0;

        try {
            List<Path> javaFiles = Files.walk(Paths.get(projectPath))
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            logger.info("Found {} Java files", javaFiles.size());

            for (Path file : javaFiles) {
                boolean fileSucceeded = true;
                try {
                    List<ApiMetadata> metadataList = javaCodeParser.parse(file);
                    totalDocuments += metadataList.size();

                    for (ApiMetadata metadata : metadataList) {
                        boolean metadataSaved = saveMetadata(metadata, file.toString());
                        boolean vectorIndexed = indexMetadataDocument(metadata, file.toString());
                        if (!metadataSaved || !vectorIndexed) {
                            fileSucceeded = false;
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    logger.error("Failed to process Java source '{}'", file, e);
                    fileSucceeded = false;
                } catch (Exception e) {
                    logger.error("Unexpected failure while processing Java source '{}'", file, e);
                    fileSucceeded = false;
                }

                if (fileSucceeded) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }
            logger.info("Indexing completed: {} success, {} failures", successCount, failureCount);

        } catch (IOException e) {
            logger.error("Failed to index Java project", e);
            failureCount++;
        }

        long duration = System.currentTimeMillis() - startTime;
        return new IndexStats(totalDocuments, successCount, failureCount, duration);
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
                    String content = new String(Files.readAllBytes(Paths.get(source.getLocation())));
                    Metadata metadataObj = new Metadata();
                    metadataObj.put("source", source.getLocation());
                    metadataObj.put("type", source.getType().name());
                    Document doc = Document.from(content, metadataObj);
                    documentProcessor.processAndStore(doc);
                    successCount++;
                } else {
                    logger.warn("Skipping unsupported external source type={} location={}",
                            source.getType(), source.getLocation());
                    failureCount++;
                }
            } catch (IOException | RuntimeException e) {
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
        Embedding queryEmbedding;
        try {
            queryEmbedding = embeddingModel.embed(query).content();
        } catch (Exception e) {
            logger.error("Failed to embed query '{}', degrading to empty result", query, e);
            return List.of();
        }

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
                    } else {
                        logger.warn("Metadata missing for apiId='{}', returning degraded vector hit", apiId);
                        results.add(buildDegradedResult(apiId, segment));
                    }
                } catch (Exception e) {
                    logger.error("Failed to load metadata for apiId='{}', returning degraded vector hit", apiId, e);
                    results.add(buildDegradedResult(apiId, segment));
                }
            } else {
                logger.warn("Vector hit without apiId metadata, returning degraded result for source={}",
                        segment.metadata("source"));
                results.add(buildDegradedResult(null, segment));
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
                String content = new String(Files.readAllBytes(Paths.get(filePath)));
                Metadata metadataObj = new Metadata();
                metadataObj.put("source", filePath);
                Document doc = Document.from(content, metadataObj);
                documentProcessor.processAndStore(doc);
            } catch (IOException | RuntimeException e) {
                logger.error("Failed to update index for: {}", filePath, e);
            }
        }
    }

    private boolean saveMetadata(ApiMetadata metadata, String source) {
        try {
            metadataStore.save(metadata);
            return true;
        } catch (SQLException | RuntimeException e) {
            logger.error("Failed to persist metadata for source='{}' apiId='{}'",
                    source, metadata != null ? metadata.getApiId() : null, e);
            return false;
        }
    }

    private boolean indexMetadataDocument(ApiMetadata metadata, String source) {
        try {
            Metadata metadataObj = new Metadata();
            metadataObj.put("source", source);
            metadataObj.put("apiId", metadata.getApiId());
            metadataObj.put("type", DocumentSourceType.INTERNAL_CODE.name());
            Document doc = Document.from(buildIndexContent(metadata), metadataObj);
            documentProcessor.processAndStore(doc);
            return true;
        } catch (RuntimeException e) {
            logger.error("Failed to store vector document for source='{}' apiId='{}'",
                    source, metadata != null ? metadata.getApiId() : null, e);
            return false;
        }
    }

    private String buildIndexContent(ApiMetadata metadata) {
        String description = metadata.getDescription() != null ? metadata.getDescription() : "";
        String className = metadata.getClassName() != null ? metadata.getClassName() : "";
        String methodName = metadata.getMethodName() != null ? metadata.getMethodName() : "";
        return (description + " " + className + "." + methodName).trim();
    }

    private ApiMetadata buildDegradedResult(String apiId, TextSegment segment) {
        return ApiMetadata.builder()
                .apiId(apiId)
                .description(segment != null ? segment.text() : null)
                .sourceLocation(segment != null ? segment.metadata("source") : null)
                .sourceType(parseSourceType(segment != null ? segment.metadata("type") : null))
                .build();
    }

    private DocumentSourceType parseSourceType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return DocumentSourceType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown source type '{}' found in vector metadata, degrading to null", value);
            return null;
        }
    }
}
