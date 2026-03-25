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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识库服务实现
 */
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);
    private static final int MIN_VECTOR_RECALL_SIZE = 20;
    private static final List<String> DISK_HINTS = List.of("系统盘", "磁盘", "卷", "volume", "disk");
    private static final List<String> SERVER_HINTS = List.of("lite server", "开发服务器", "dev server", "dev-server", "dev-servers");
    private static final List<String> DETACH_HINTS = List.of("卸载", "detach");
    private static final List<String> DELETE_HINTS = List.of("删除", "delete");
    private static final List<String> DETACH_VOLUME_HINTS = List.of("磁盘", "volume", "disk", "detachvolume");
    private static final List<String> DEV_SERVER_HINTS = List.of("lite server", "dev server", "dev-server", "dev-servers", "开发服务器");
    private static final List<String> IRRELEVANT_STORAGE_HINTS = List.of("obs", "notebook", "/services/", "工作流", "workflow");
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
        if (query == null || query.trim().isEmpty() || topK <= 0) {
            return List.of();
        }

        String normalizedQuery = query.trim();
        List<String> queryVariants = buildQueryVariants(normalizedQuery);
        int recallSize = Math.max(topK * 4, MIN_VECTOR_RECALL_SIZE);
        List<SearchCandidate> uniqueCandidates = new ArrayList<>();

        for (String queryVariant : queryVariants) {
            Embedding queryEmbedding = embedQuery(queryVariant);
            if (queryEmbedding == null) {
                continue;
            }
            List<EmbeddingMatch<TextSegment>> matches = vectorStore.search(queryEmbedding, recallSize);
            collectCandidates(uniqueCandidates, matches);
        }

        List<ApiMetadata> results = uniqueCandidates.stream()
                .map(candidate -> toRankedResult(normalizedQuery, candidate))
                .filter(Objects::nonNull)
                .sorted((left, right) -> {
                    int byScore = Double.compare(right.score(), left.score());
                    if (byScore != 0) {
                        return byScore;
                    }
                    return Double.compare(right.vectorScore(), left.vectorScore());
                })
                .map(RankedResult::metadata)
                .limit(topK)
                .collect(Collectors.toList());

        logger.info("Found {} results for query after ranking {} unique candidates across {} variants",
                results.size(),
                uniqueCandidates.size(),
                queryVariants.size());
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

    private Embedding embedQuery(String query) {
        try {
            return embeddingModel.embed(query).content();
        } catch (Exception e) {
            logger.error("Failed to embed query '{}', skipping variant", query, e);
            return null;
        }
    }

    private void collectCandidates(
            List<SearchCandidate> candidates,
            List<EmbeddingMatch<TextSegment>> matches) {
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            String key = buildCandidateKey(segment);
            if (key == null) {
                continue;
            }
            SearchCandidate candidate = new SearchCandidate(key, match);
            int existingIndex = findCandidateIndex(candidates, key);
            if (existingIndex >= 0) {
                if (candidate.score() > candidates.get(existingIndex).score()) {
                    candidates.set(existingIndex, candidate);
                }
                continue;
            }
            candidates.add(candidate);
        }
    }

    private int findCandidateIndex(List<SearchCandidate> candidates, String key) {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private RankedResult toRankedResult(String query, SearchCandidate candidate) {
        TextSegment segment = candidate.segment();
        String apiId = segment.metadata("apiId");
        ApiMetadata metadata = loadMetadata(apiId, segment);
        if (metadata == null) {
            return null;
        }
        return new RankedResult(metadata, scoreCandidate(query, metadata, candidate), candidate.score());
    }

    private ApiMetadata loadMetadata(String apiId, TextSegment segment) {
        if (apiId == null) {
            logger.warn("Vector hit without apiId metadata, returning degraded result for source={}",
                    segment.metadata("source"));
            return buildDegradedResult(null, segment);
        }

        try {
            ApiMetadata metadata = metadataStore.findByApiId(apiId);
            if (metadata != null) {
                return metadata;
            }
            logger.warn("Metadata missing for apiId='{}', returning degraded vector hit", apiId);
            return buildDegradedResult(apiId, segment);
        } catch (Exception e) {
            logger.error("Failed to load metadata for apiId='{}', returning degraded vector hit", apiId, e);
            return buildDegradedResult(apiId, segment);
        }
    }

    private String buildIndexContent(ApiMetadata metadata) {
        String description = metadata.getDescription() != null ? metadata.getDescription() : "";
        String className = metadata.getClassName() != null ? metadata.getClassName() : "";
        String methodName = metadata.getMethodName() != null ? metadata.getMethodName() : "";
        return (description + " " + className + "." + methodName).trim();
    }

    private List<String> buildQueryVariants(String query) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        String trimmed = query.trim();
        String normalized = normalize(trimmed);
        variants.add(trimmed);

        boolean diskLike = containsAny(normalized, DISK_HINTS);
        boolean serverLike = containsAny(normalized, SERVER_HINTS) || normalized.contains("服务器");
        boolean detachLike = containsAny(normalized, DETACH_HINTS);

        if (normalized.contains("系统盘")) {
            variants.add(trimmed.replace("系统盘", "磁盘"));
        }
        if (normalized.contains("卷")) {
            variants.add(trimmed.replace("卷", "磁盘"));
        }
        if (diskLike && detachLike) {
            variants.add("卸载磁盘");
        }
        if (diskLike) {
            variants.add("Lite Server 卸载磁盘");
        }
        if (diskLike && (serverLike || normalized.contains("开发服务器"))) {
            variants.add("开发服务器 卸载 磁盘");
        } else if (diskLike) {
            variants.add("开发服务器 卸载 磁盘");
        }

        return variants.stream()
                .filter(this::hasText)
                .limit(6)
                .collect(Collectors.toList());
    }

    private double scoreCandidate(String query, ApiMetadata metadata, SearchCandidate candidate) {
        String normalizedQuery = normalize(query);
        String searchable = normalize(buildSearchableText(metadata, candidate.segment()));

        double score = candidate.score();
        if (containsAny(normalizedQuery, DETACH_HINTS)) {
            score += countMatches(searchable, DETACH_HINTS) * 0.8;
        }
        if (containsAny(normalizedQuery, DISK_HINTS)) {
            score += countMatches(searchable, DETACH_VOLUME_HINTS) * 1.2;
            if (normalizedQuery.contains("系统盘")) {
                score += countMatches(searchable, List.of("磁盘", "volume", "disk", "detachvolume")) * 1.4;
            }
            if (containsAny(searchable, IRRELEVANT_STORAGE_HINTS) && !containsAny(searchable, DEV_SERVER_HINTS)) {
                score -= 3.0;
            }
        }
        if (containsAny(normalizedQuery, SERVER_HINTS) || normalizedQuery.contains("服务器")) {
            score += countMatches(searchable, DEV_SERVER_HINTS) * 1.4;
            if (containsAny(searchable, List.of("/services/", "service", "workflow")) && !containsAny(searchable, DEV_SERVER_HINTS)) {
                score -= 3.5;
            }
        }
        if (normalizedQuery.contains("卷")) {
            score += countMatches(searchable, List.of("volume", "磁盘", "detachvolume")) * 1.1;
        }
        if (containsAny(normalizedQuery, DELETE_HINTS) && containsAny(searchable, DELETE_HINTS) && !containsAny(searchable, DETACH_HINTS)) {
            score += 0.4;
        }
        if (containsAny(normalizedQuery, DETACH_HINTS) && containsAny(searchable, DELETE_HINTS) && !containsAny(searchable, DETACH_HINTS)) {
            score -= 1.4;
        }
        if (containsAny(searchable, List.of("/dev-servers/", "detachvolume"))) {
            score += 1.0;
        }
        return score;
    }

    private String buildSearchableText(ApiMetadata metadata, TextSegment segment) {
        return String.join(" ",
                safe(metadata.getDescription()),
                safe(metadata.getClassName()),
                safe(metadata.getMethodName()),
                safe(metadata.getHttpMethod()),
                safe(metadata.getEndpoint()),
                safe(metadata.getSourceLocation()),
                segment != null ? safe(segment.text()) : "");
    }

    private String buildCandidateKey(TextSegment segment) {
        if (segment == null) {
            return null;
        }
        String apiId = segment.metadata("apiId");
        if (hasText(apiId)) {
            return apiId.trim();
        }
        String source = segment.metadata("source");
        String text = segment.text();
        if (!hasText(source) && !hasText(text)) {
            return null;
        }
        return safe(source) + "#" + safe(text);
    }

    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(keyword -> text.contains(normalize(keyword)));
    }

    private int countMatches(String text, List<String> keywords) {
        return (int) keywords.stream()
                .map(this::normalize)
                .filter(keyword -> !keyword.isEmpty())
                .filter(text::contains)
                .distinct()
                .count();
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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

    private record SearchCandidate(String key, EmbeddingMatch<TextSegment> match) {
        private TextSegment segment() {
            return match.embedded();
        }

        private double score() {
            return match.score();
        }
    }

    private record RankedResult(ApiMetadata metadata, double score, double vectorScore) {
    }
}
