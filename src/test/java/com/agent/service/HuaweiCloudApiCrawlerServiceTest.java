package com.agent.service;

import com.agent.crawler.WebDocumentCrawler;
import com.agent.model.ApiMetadata;
import com.agent.parser.HuaweiCloudApiParser;
import com.agent.processor.DocumentProcessor;
import com.agent.storage.MetadataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HuaweiCloudApiCrawlerServiceTest {

    @Mock
    private WebDocumentCrawler webCrawler;

    @Mock
    private MetadataStore metadataStore;

    @Mock
    private DocumentProcessor documentProcessor;

    private final HuaweiCloudApiParser parser = new HuaweiCloudApiParser();

    @Test
    void crawlsDirectoryPageAndIndexesStructuredApis() throws IOException, SQLException {
        String directoryUrl = "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html";
        String listUrl = "https://support.huaweicloud.com/api-modelarts/ListWorkflows.html";
        String createUrl = "https://support.huaweicloud.com/api-modelarts/CreateWorkflow.html";

        when(webCrawler.fetchHtml(directoryUrl)).thenReturn("""
                <html><body>
                  <a href="ListWorkflows.html">ListWorkflows</a>
                  <a href="CreateWorkflow.html">CreateWorkflow</a>
                  <a href="modelarts_03_0003.html">ignore directory</a>
                </body></html>
                """);
        when(webCrawler.fetchHtml(listUrl)).thenReturn("""
                <html><head><title>获取Workflow工作流列表 - ListWorkflows</title></head><body><article>
                  <h4>URI</h4>
                  <p>GET /v2/{project_id}/workflows</p>
                </article></body></html>
                """);
        when(webCrawler.fetchHtml(createUrl)).thenReturn("""
                <html><head><title>创建Workflow - CreateWorkflow</title></head><body><article>
                  <h4>URI</h4>
                  <p>POST /v2/{project_id}/workflows</p>
                </article></body></html>
                """);
        doNothing().when(metadataStore).save(any(ApiMetadata.class));

        HuaweiCloudApiCrawlerService service = new HuaweiCloudApiCrawlerService(
                webCrawler,
                parser,
                metadataStore,
                documentProcessor);

        HuaweiCloudApiCrawlerService.CrawlResult result = service.crawlAndIndex(directoryUrl);

        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        verify(metadataStore, times(2)).save(any(ApiMetadata.class));
        verify(documentProcessor, times(2)).processAndStore(any());

        ArgumentCaptor<ApiMetadata> apiCaptor = ArgumentCaptor.forClass(ApiMetadata.class);
        verify(metadataStore, times(2)).save(apiCaptor.capture());
        List<ApiMetadata> savedApis = apiCaptor.getAllValues();
        assertEquals(List.of("ListWorkflows", "CreateWorkflow"),
                savedApis.stream().map(ApiMetadata::getMethodName).toList());
        assertTrue(savedApis.stream().anyMatch(api -> listUrl.equals(api.getSourceLocation())));
        assertTrue(savedApis.stream().anyMatch(api -> createUrl.equals(api.getSourceLocation())));
    }
}
