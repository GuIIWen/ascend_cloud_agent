package com.agent.service;

import com.agent.model.ApiMetadata;
import com.agent.model.DocumentSource;
import com.agent.model.DocumentSourceType;
import com.agent.parser.HuaweiCloudApiParser;
import com.agent.storage.MetadataStore;
import com.agent.processor.DocumentProcessor;
import com.agent.crawler.WebDocumentCrawler;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 华为云API文档抓取服务
 */
public class HuaweiCloudApiCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(HuaweiCloudApiCrawlerService.class);

    private final WebDocumentCrawler webCrawler;
    private final HuaweiCloudApiParser apiParser;
    private final MetadataStore metadataStore;
    private final DocumentProcessor documentProcessor;

    public HuaweiCloudApiCrawlerService(
            WebDocumentCrawler webCrawler,
            HuaweiCloudApiParser apiParser,
            MetadataStore metadataStore,
            DocumentProcessor documentProcessor) {
        this.webCrawler = webCrawler;
        this.apiParser = apiParser;
        this.metadataStore = metadataStore;
        this.documentProcessor = documentProcessor;
    }

    /**
     * 抓取并解析华为云API文档
     */
    public CrawlResult crawlAndIndex(String url) {
        logger.info("Starting to crawl Huawei Cloud API doc: {}", url);
        long startTime = System.currentTimeMillis();

        List<ApiMetadata> indexedApis = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            // 抓取网页内容
            Document doc = webCrawler.crawl(url);
            String htmlContent = doc.text();
            String sourceUrl = url;

            // 解析API信息
            List<ApiMetadata> apis = apiParser.parse(htmlContent, sourceUrl);
            logger.info("Parsed {} APIs from document", apis.size());

            // 保存每个API到元数据存储
            for (ApiMetadata api : apis) {
                try {
                    metadataStore.save(api);
                    indexedApis.add(api);

                    // 同时添加到向量存储
                    String content = buildContent(api);
                    Metadata metadata = new Metadata();
                    metadata.put("source", sourceUrl);
                    metadata.put("apiId", api.getApiId());
                    metadata.put("type", DocumentSourceType.WEB_PAGE.name());
                    Document vectorDoc = Document.from(content, metadata);
                    documentProcessor.processAndStore(vectorDoc);

                } catch (SQLException e) {
                    logger.error("Failed to save API metadata: {}", api.getMethodName(), e);
                    errors.add("Failed to save: " + api.getMethodName() + " - " + e.getMessage());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Crawl completed: {} APIs indexed, {} errors, duration: {}ms",
                    indexedApis.size(), errors.size(), duration);

            return new CrawlResult(indexedApis.size(), errors.size(), duration, errors);

        } catch (IOException e) {
            logger.error("Failed to crawl URL: {}", url, e);
            errors.add("Crawl failed: " + e.getMessage());
            return new CrawlResult(0, 1, System.currentTimeMillis() - startTime, errors);
        }
    }

    /**
     * 构建API文档内容
     */
    private String buildContent(ApiMetadata api) {
        StringBuilder sb = new StringBuilder();
        sb.append("API名称: ").append(api.getMethodName()).append("\n");
        sb.append("类名: ").append(api.getClassName()).append("\n");
        if (api.getDescription() != null) {
            sb.append("描述: ").append(api.getDescription()).append("\n");
        }
        if (api.getHttpMethod() != null) {
            sb.append("HTTP方法: ").append(api.getHttpMethod()).append("\n");
        }
        if (api.getEndpoint() != null) {
            sb.append("端点: ").append(api.getEndpoint()).append("\n");
        }
        if (api.getRequestBody() != null) {
            sb.append("请求体: ").append(api.getRequestBody()).append("\n");
        }
        if (api.getResponseBody() != null) {
            sb.append("响应: ").append(api.getResponseBody()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 抓取结果
     */
    public static class CrawlResult {
        private final int successCount;
        private final int failureCount;
        private final long durationMs;
        private final List<String> errors;

        public CrawlResult(int successCount, int failureCount, long durationMs, List<String> errors) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.durationMs = durationMs;
            this.errors = errors;
        }

        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public long getDurationMs() { return durationMs; }
        public List<String> getErrors() { return errors; }
    }
}
