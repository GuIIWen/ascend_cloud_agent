package com.agent.parser;

import com.agent.model.ApiMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                    <a href="../api-modelarts/CreateWorkflow.html#section">CreateWorkflow Anchor Duplicate</a>
                    <a href="https://support.huaweicloud.com/api-modelarts/ListWorkflows.html?locale=zh-cn">ListWorkflows Query Duplicate</a>
                    <a href="modelarts_03_0003.html">Another Directory</a>
                    <a href="https://support.huaweicloud.com/other-service/ListJobs.html">Other Service</a>
                    <a href="#fragment-only">Fragment</a>
                    <a href="javascript:void(0)">Javascript</a>
                    <a href="">Empty</a>
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
    void returnsEmptyListForBlankDirectoryHtml() {
        List<String> urls = parser.discoverDetailPageUrls(
                "   ",
                "https://support.huaweicloud.com/api-modelarts/modelarts_03_0002.html");

        assertTrue(urls.isEmpty());
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

    @Test
    void returnsNullForEmptyDetailPage() {
        ApiMetadata api = parser.parseApiDetail(
                "<html><head></head><body>   </body></html>",
                "https://support.huaweicloud.com/api-modelarts/ListWorkflows.html",
                null);

        assertNull(api);
    }

    @Test
    void parsesStructuredFieldsFromHuaweiCloudDetailPage() {
        String html = """
                <html>
                <head>
                  <title>Lite Server服务器卸载磁盘 - DetachDevServerVolume</title>
                  <meta name="description" content="Lite Server服务器卸载磁盘接口"/>
                </head>
                <body>
                  <div class="section">
                    <h4 class="sectiontitle">URI</h4>
                    <p>DELETE /v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}</p>
                    <div class="tablenoborder">
                      <table>
                        <caption><b>表1 </b>路径参数</caption>
                        <thead>
                          <tr>
                            <th>参数</th>
                            <th>是否必选</th>
                            <th>参数类型</th>
                            <th>描述</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr>
                            <td>project_id</td>
                            <td>是</td>
                            <td>String</td>
                            <td>用户项目ID</td>
                          </tr>
                          <tr>
                            <td>id</td>
                            <td>是</td>
                            <td>String</td>
                            <td>Lite Server实例ID</td>
                          </tr>
                          <tr>
                            <td>volume_id</td>
                            <td>是</td>
                            <td>String</td>
                            <td>要卸载的磁盘ID</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </div>
                  <div class="section">
                    <h4 class="sectiontitle">请求参数</h4>
                    <p>无</p>
                  </div>
                  <div class="section">
                    <h4 class="sectiontitle">响应参数</h4>
                    <table>
                      <caption><b>表2 </b>响应Body参数</caption>
                      <thead>
                        <tr>
                          <th>参数</th>
                          <th>参数类型</th>
                          <th>描述</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr>
                          <td>operation_id</td>
                          <td>String</td>
                          <td>操作ID</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <div class="section">
                    <h4 class="sectiontitle">响应示例</h4>
                    <pre class="screen">{"operation_id":"UUID","operation_status":"running"}</pre>
                  </div>
                </body>
                </html>
                """;

        ApiMetadata api = parser.parseApiDetail(
                html,
                "https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html",
                null);

        assertEquals("DetachDevServerVolume", api.getMethodName());
        assertEquals("DELETE", api.getHttpMethod());
        assertEquals("/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}", api.getEndpoint());
        assertEquals(3, api.getParameters().size());
        assertEquals("project_id", api.getParameters().get(0).getName());
        assertTrue(api.getParameters().get(0).isRequired());
        assertEquals("无", api.getRequestBody());
        assertFalse(api.getResponseBody().isBlank());
        assertTrue(api.getResponseBody().contains("operation_id"));
    }
}
