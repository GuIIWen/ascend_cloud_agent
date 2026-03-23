package com.agent.parser;

import com.agent.model.ApiMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HuaweiCloudApiParserTest {

    private final HuaweiCloudApiParser parser = new HuaweiCloudApiParser();

    @Test
    void discoversOnlySupportedDetailPagesFromDirectoryHtml() {
        String sourceUrl = "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html";
        String html = """
                <html>
                <body>
                  <div class="toc">
                    <a href="ListWorkflows.html">ListWorkflows</a>
                    <a href="/api-modelarts/CreateWorkflow.html">CreateWorkflow</a>
                    <a href="modelarts_03_0003.html">Another Directory</a>
                    <a href="https://support.huaweicloud.com/other-service/ListJobs.html">Other Service</a>
                    <a href="#fragment-only">Fragment</a>
                  </div>
                </body>
                </html>
                """;

        List<String> urls = parser.discoverDetailPageUrls(html, sourceUrl);

        assertEquals(List.of(
                "https://support.huaweicloud.com/api-modelarts/ListWorkflows.html",
                "https://support.huaweicloud.com/api-modelarts/CreateWorkflow.html"),
                urls);
    }

    @Test
    void parsesApiDetailFromUriSection() {
        String html = """
                <html>
                <head>
                  <title>获取Workflow工作流列表 - ListWorkflows_Workflow工作流管理_API参考</title>
                  <meta name="description" content="列出当前租户下的工作流"/>
                </head>
                <body>
                  <article>
                    <h4 class="sectiontitle">URI</h4>
                    <p>GET /v2/{project_id}/workflows</p>
                    <p>GET https://{endpoint}/v2/{project_id}/workflows</p>
                    <h4>请求参数</h4>
                    <p>project_id String</p>
                  </article>
                </body>
                </html>
                """;

        ApiMetadata api = parser.parseApiDetail(
                html,
                "https://support.huaweicloud.com/api-modelarts/ListWorkflows.html",
                null);

        assertEquals("ListWorkflows", api.getMethodName());
        assertEquals("GET", api.getHttpMethod());
        assertEquals("/v2/{project_id}/workflows", api.getEndpoint());
        assertTrue(api.getDescription().contains("列出当前租户下的工作流"));
        assertEquals("https://support.huaweicloud.com/api-modelarts/ListWorkflows.html", api.getSourceLocation());
    }
}
