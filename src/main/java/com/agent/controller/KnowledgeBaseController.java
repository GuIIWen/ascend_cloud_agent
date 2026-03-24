package com.agent.controller;

import com.agent.model.ApiMetadata;
import com.agent.model.DocumentSource;
import com.agent.model.DocumentSourceType;
import com.agent.model.error.ApiErrorResponse;
import com.agent.service.HuaweiCloudApiCrawlerService;
import com.agent.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库REST控制器
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeBaseController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseController.class);
    private static final int DEFAULT_TOP_K = 5;

    private final KnowledgeBaseService knowledgeBaseService;
    private final HuaweiCloudApiCrawlerService huaweiCloudApiCrawlerService;

    public KnowledgeBaseController(
            KnowledgeBaseService knowledgeBaseService,
            HuaweiCloudApiCrawlerService huaweiCloudApiCrawlerService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.huaweiCloudApiCrawlerService = huaweiCloudApiCrawlerService;
    }

    /**
     * 搜索API
     */
    @PostMapping(
            value = "/search",
            consumes = "application/json",
            produces = "application/json")
    public Object search(@RequestBody SearchRequest request) {
        ApiErrorResponse validationError = validateSearchRequest(request);
        if (validationError != null) {
            return validationError;
        }

        logger.info("Search request: query='{}', topK={}", request.getQuery(), request.getTopK());
        int topK = request.getTopK() != null ? request.getTopK() : DEFAULT_TOP_K;

        List<ApiMetadata> results = knowledgeBaseService.search(
                request.getQuery().trim(),
                topK);

        Map<String, Object> response = new HashMap<>();
        response.put("query", request.getQuery().trim());
        response.put("total", results.size());
        response.put("results", results);
        return response;
    }

    /**
     * 索引外部文档（支持网页）
     */
    @PostMapping(
            value = "/index",
            consumes = "application/json",
            produces = "application/json")
    public Object index(@RequestBody IndexRequest request) {
        ApiErrorResponse validationError = validateUrlRequest("url", request != null ? request.getUrl() : null);
        if (validationError != null) {
            return validationError;
        }

        logger.info("Index request: url='{}'", request.getUrl());

        List<DocumentSource> sources = new ArrayList<>();
        DocumentSource source = new DocumentSource();
        source.setId("web-doc-" + System.currentTimeMillis());
        source.setName(request.getName() != null ? request.getName() : "Web Document");
        source.setType(DocumentSourceType.WEB_PAGE);
        source.setLocation(request.getUrl().trim());
        source.setEnabled(true);
        sources.add(source);

        KnowledgeBaseService.IndexStats stats = knowledgeBaseService.indexExternalDocs(sources);

        Map<String, Object> response = new HashMap<>();
        response.put("success", stats.getFailureCount() == 0);
        response.put("totalDocuments", stats.getTotalDocuments());
        response.put("successCount", stats.getSuccessCount());
        response.put("failureCount", stats.getFailureCount());
        response.put("durationMs", stats.getDurationMs());
        return response;
    }

    /**
     * 索引Java项目
     */
    @PostMapping(
            value = "/index/java",
            consumes = "application/json",
            produces = "application/json")
    public Object indexJavaProject(@RequestBody IndexJavaRequest request) {
        String projectPath = request != null ? request.getProjectPath() : null;
        ApiErrorResponse validationError = validateBlankField("projectPath", projectPath);
        if (validationError != null) {
            return validationError;
        }

        logger.info("Index Java project: {}", projectPath);

        KnowledgeBaseService.IndexStats stats = knowledgeBaseService.indexJavaProject(projectPath.trim());

        Map<String, Object> response = new HashMap<>();
        response.put("success", stats.getFailureCount() == 0);
        response.put("totalDocuments", stats.getTotalDocuments());
        response.put("successCount", stats.getSuccessCount());
        response.put("failureCount", stats.getFailureCount());
        response.put("durationMs", stats.getDurationMs());
        return response;
    }

    /**
     * 抓取华为云API文档并索引
     * 专门用于解析华为云ModelArts等服务的API文档
     */
    @PostMapping(
            value = "/crawl/huawei-cloud",
            consumes = "application/json",
            produces = "application/json")
    public Object crawlHuaweiCloudApi(@RequestBody CrawlRequest request) {
        String url = request != null ? request.getUrl() : null;
        ApiErrorResponse validationError = validateUrlRequest("url", url);
        if (validationError != null) {
            return validationError;
        }

        logger.info("Crawl Huawei Cloud API request: url='{}'", url);

        HuaweiCloudApiCrawlerService.CrawlResult result = huaweiCloudApiCrawlerService.crawlAndIndex(url.trim());

        Map<String, Object> response = new HashMap<>();
        response.put("success", result.getFailureCount() == 0);
        response.put("successCount", result.getSuccessCount());
        response.put("failureCount", result.getFailureCount());
        response.put("durationMs", result.getDurationMs());
        response.put("errors", result.getErrors());
        return response;
    }

    private ApiErrorResponse validateSearchRequest(SearchRequest request) {
        if (request == null) {
            return validationError("request", "Request body must not be empty");
        }
        ApiErrorResponse queryError = validateBlankField("query", request.getQuery());
        if (queryError != null) {
            return queryError;
        }
        Integer topK = request.getTopK();
        if (topK != null && topK <= 0) {
            return validationError("topK", "topK must be greater than 0");
        }
        return null;
    }

    private ApiErrorResponse validateUrlRequest(String field, String value) {
        ApiErrorResponse blankError = validateBlankField(field, value);
        if (blankError != null) {
            return blankError;
        }
        String normalized = value.trim();
        if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            return validationError(field, field + " must start with http:// or https://");
        }
        return null;
    }

    private ApiErrorResponse validateBlankField(String field, String value) {
        if (value == null || value.trim().isEmpty()) {
            return validationError(field, field + " must not be blank");
        }
        return null;
    }

    private ApiErrorResponse validationError(String field, String message) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("field", field);
        return ApiErrorResponse.of("INVALID_ARGUMENT", message, details);
    }

    /**
     * 搜索请求
     */
    public static class SearchRequest {
        private String query;
        private Integer topK;

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public Integer getTopK() { return topK; }
        public void setTopK(Integer topK) { this.topK = topK; }
    }

    /**
     * 索引请求
     */
    public static class IndexRequest {
        private String url;
        private String name;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /**
     * Java项目索引请求
     */
    public static class IndexJavaRequest {
        private String projectPath;

        public String getProjectPath() { return projectPath; }
        public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    }

    /**
     * 抓取请求
     */
    public static class CrawlRequest {
        private String url;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
