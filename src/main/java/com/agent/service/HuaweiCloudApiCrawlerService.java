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
            String htmlContent = webCrawler.fetchHtml(url);
            List<ApiMetadata> apis = new ArrayList<>();

            if (apiParser.isDirectoryPage(url)) {
                List<String> detailUrls = apiParser.discoverDetailPageUrls(htmlContent, url);
                logger.info("Discovered {} detail pages from directory {}", detailUrls.size(), url);

                for (String detailUrl : detailUrls) {
                    try {
                        String detailHtml = webCrawler.fetchHtml(detailUrl);
                        ApiMetadata api = apiParser.parseApiDetail(detailHtml, detailUrl, null);
                        if (api != null) {
                            apis.add(api);
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to crawl detail page: {}", detailUrl, e);
                        errors.add("Failed to crawl detail page: " + detailUrl + " - " + e.getMessage());
                    }
                }
            } else {
                apis = apiParser.parse(htmlContent, url);
            }

            logger.info("Parsed {} APIs from document", apis.size());
            saveApis(apis, indexedApis, errors);

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

    private void saveApis(List<ApiMetadata> apis, List<ApiMetadata> indexedApis, List<String> errors) {
        for (ApiMetadata api : apis) {
            try {
                metadataStore.save(api);
                indexedApis.add(api);

                String content = buildContent(api);
                Metadata metadata = new Metadata();
                metadata.put("source", api.getSourceLocation());
                metadata.put("apiId", api.getApiId());
                metadata.put("type", DocumentSourceType.WEB_PAGE.name());
                Document vectorDoc = Document.from(content, metadata);
                documentProcessor.processAndStore(vectorDoc);

            } catch (SQLException e) {
                logger.error("Failed to save API metadata: {}", api.getMethodName(), e);
                errors.add("Failed to save: " + api.getMethodName() + " - " + e.getMessage());
            }
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
