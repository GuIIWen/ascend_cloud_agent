package com.agent.controller;

import com.agent.model.ApiMetadata;
import com.agent.model.DocumentSource;
import com.agent.model.DocumentSourceType;
import com.agent.service.HuaweiCloudApiCrawlerService;
import com.agent.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库REST控制器
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeBaseController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseController.class);

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private HuaweiCloudApiCrawlerService huaweiCloudApiCrawlerService;

    /**
     * 搜索API
     */
    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody SearchRequest request) {
        logger.info("Search request: query='{}', topK={}", request.getQuery(), request.getTopK());

        List<ApiMetadata> results = knowledgeBaseService.search(
                request.getQuery(),
                request.getTopK() != null ? request.getTopK() : 5);

        Map<String, Object> response = new HashMap<>();
        response.put("query", request.getQuery());
        response.put("total", results.size());
        response.put("results", results);
        return response;
    }

    /**
     * 索引外部文档（支持网页）
     */
    @PostMapping("/index")
    public Map<String, Object> index(@RequestBody IndexRequest request) {
        logger.info("Index request: url='{}'", request.getUrl());

        List<DocumentSource> sources = new ArrayList<>();
        DocumentSource source = new DocumentSource();
        source.setId("web-doc-" + System.currentTimeMillis());
        source.setName(request.getName() != null ? request.getName() : "Web Document");
        source.setType(DocumentSourceType.WEB_PAGE);
        source.setLocation(request.getUrl());
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
    @PostMapping("/index/java")
    public Map<String, Object> indexJavaProject(@RequestBody Map<String, String> request) {
        String projectPath = request.get("projectPath");
        logger.info("Index Java project: {}", projectPath);

        KnowledgeBaseService.IndexStats stats = knowledgeBaseService.indexJavaProject(projectPath);

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
    @PostMapping("/crawl/huawei-cloud")
    public Map<String, Object> crawlHuaweiCloudApi(@RequestBody CrawlRequest request) {
        String url = request.getUrl();
        logger.info("Crawl Huawei Cloud API request: url='{}'", url);

        HuaweiCloudApiCrawlerService.CrawlResult result = huaweiCloudApiCrawlerService.crawlAndIndex(url);

        Map<String, Object> response = new HashMap<>();
        response.put("success", result.getFailureCount() == 0);
        response.put("successCount", result.getSuccessCount());
        response.put("failureCount", result.getFailureCount());
        response.put("durationMs", result.getDurationMs());
        response.put("errors", result.getErrors());
        return response;
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
     * 抓取请求
     */
    public static class CrawlRequest {
        private String url;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
