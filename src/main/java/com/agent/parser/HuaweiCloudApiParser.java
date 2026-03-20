package com.agent.parser;

import com.agent.model.ApiMetadata;
import com.agent.model.DocumentSourceType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 华为云API文档解析器
 * 专门用于解析华为云ModelArts等服务的API文档页面
 */
public class HuaweiCloudApiParser {

    private static final Logger logger = LoggerFactory.getLogger(HuaweiCloudApiParser.class);

    // API信息模式
    private static final Pattern API_NAME_PATTERN = Pattern.compile("(Create|Delete|Update|Query|List|Attach|Detach|Start|Stop|Restart|Modify|Describe|Get)[A-Za-z0-9_]+");
    private static final Pattern HTTP_METHOD_PATTERN = Pattern.compile("(GET|POST|PUT|DELETE|PATCH)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENDPOINT_PATTERN = Pattern.compile("/[a-z0-9/\\-\\{\\}]+", Pattern.CASE_INSENSITIVE);

    /**
     * 解析华为云API文档页面
     */
    public List<ApiMetadata> parse(String htmlContent, String sourceUrl) {
        List<ApiMetadata> apis = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(htmlContent);

            // 提取页面标题作为文档标题
            String pageTitle = doc.title();
            String apiDescription = extractApiDescription(doc);

            // 尝试查找API表格或列表
            Elements tables = doc.select("table");
            for (Element table : tables) {
                List<ApiMetadata> tableApis = parseTable(table, sourceUrl, pageTitle);
                apis.addAll(tableApis);
            }

            // 如果没有找到表格，尝试从标题和段落中提取
            if (apis.isEmpty()) {
                apis = extractFromHeadings(doc, sourceUrl, pageTitle, apiDescription);
            }

            logger.info("Parsed {} APIs from Huawei Cloud document: {}", apis.size(), sourceUrl);
        } catch (Exception e) {
            logger.error("Failed to parse Huawei Cloud API document: {}", sourceUrl, e);
        }

        return apis;
    }

    /**
     * 从表格中解析API信息
     */
    private List<ApiMetadata> parseTable(Element table, String sourceUrl, String pageTitle) {
        List<ApiMetadata> apis = new ArrayList<>();
        Elements rows = table.select("tbody tr");

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() >= 2) {
                String cellText = cells.text();
                Matcher matcher = API_NAME_PATTERN.matcher(cellText);
                if (matcher.find()) {
                    String apiName = matcher.group();
                    String description = cells.size() > 1 ? cells.get(1).text() : "";

                    ApiMetadata api = ApiMetadata.builder()
                            .apiId(generateApiId(apiName, sourceUrl))
                            .className(extractClassName(apiName))
                            .methodName(apiName)
                            .description(description)
                            .sourceType(DocumentSourceType.WEB_PAGE)
                            .sourceLocation(sourceUrl)
                            .httpMethod(extractHttpMethod(cells.text()))
                            .endpoint(extractEndpoint(cells.text()))
                            .build();

                    apis.add(api);
                }
            }
        }

        return apis;
    }

    /**
     * 从标题和段落中提取API信息
     */
    private List<ApiMetadata> extractFromHeadings(Document doc, String sourceUrl, String pageTitle, String description) {
        List<ApiMetadata> apis = new ArrayList<>();

        // 查找所有标题
        Elements headings = doc.select("h1, h2, h3, h4");
        for (Element heading : headings) {
            String text = heading.text();
            Matcher matcher = API_NAME_PATTERN.matcher(text);
            if (matcher.find()) {
                String apiName = matcher.group();

                // 尝试获取该API对应的详细描述
                Element nextElement = heading.nextElementSibling();
                String apiDescription = description;
                if (nextElement != null && nextElement.tagName().equals("p")) {
                    apiDescription = nextElement.text();
                }

                ApiMetadata api = ApiMetadata.builder()
                        .apiId(generateApiId(apiName, sourceUrl))
                        .className(extractClassName(apiName))
                        .methodName(apiName)
                        .description(apiDescription)
                        .sourceType(DocumentSourceType.WEB_PAGE)
                        .sourceLocation(sourceUrl)
                        .httpMethod(detectHttpMethod(text))
                        .endpoint(detectEndpoint(nextElement != null ? nextElement.text() : ""))
                        .build();

                apis.add(api);
            }
        }

        return apis;
    }

    /**
     * 提取API描述
     */
    private String extractApiDescription(Document doc) {
        Element content = doc.selectFirst("article, main, .content, #content");
        if (content != null) {
            Element firstPara = content.selectFirst("p");
            if (firstPara != null) {
                return firstPara.text();
            }
        }
        return doc.title();
    }

    /**
     * 生成API ID
     */
    private String generateApiId(String apiName, String sourceUrl) {
        return "huawei-" + apiName.toLowerCase() + "-" + sourceUrl.hashCode();
    }

    /**
     * 从API名称提取类名
     */
    private String extractClassName(String apiName) {
        // 将驼峰命名转换为单词
        StringBuilder className = new StringBuilder();
        for (char c : apiName.toCharArray()) {
            if (Character.isUpperCase(c) && className.length() > 0) {
                className.append('_');
            }
            className.append(c);
        }
        return className.toString().toUpperCase();
    }

    /**
     * 提取HTTP方法
     */
    private String extractHttpMethod(String text) {
        Matcher matcher = HTTP_METHOD_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group().toUpperCase();
        }
        return "POST"; // 默认POST
    }

    /**
     * 检测HTTP方法
     */
    private String detectHttpMethod(String text) {
        String lowerText = text.toLowerCase();
        if (lowerText.contains("query") || lowerText.contains("get") || lowerText.contains("list")) {
            return "GET";
        } else if (lowerText.contains("delete") || lowerText.contains("remove")) {
            return "DELETE";
        } else if (lowerText.contains("update") || lowerText.contains("modify")) {
            return "PUT";
        }
        return "POST";
    }

    /**
     * 提取端点路径
     */
    private String extractEndpoint(String text) {
        Matcher matcher = ENDPOINT_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return "/api/v1/devserver";
    }

    /**
     * 检测端点路径
     */
    private String detectEndpoint(String text) {
        Matcher matcher = ENDPOINT_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        // 默认端点
        if (text.contains("devserver")) {
            return "/api/v1/devserver";
        } else if (text.contains("volume")) {
            return "/api/v1/volume";
        } else if (text.contains("job")) {
            return "/api/v1/job";
        }
        return "/api/v1/resource";
    }

    /**
     * 解析完整的API文档（包含详细信息）
     */
    public ApiMetadata parseApiDetail(String htmlContent, String sourceUrl, String apiName) {
        Document doc = Jsoup.parse(htmlContent);

        ApiMetadata.Builder builder = ApiMetadata.builder()
                .apiId(generateApiId(apiName, sourceUrl))
                .methodName(apiName)
                .className(extractClassName(apiName))
                .sourceType(DocumentSourceType.WEB_PAGE)
                .sourceLocation(sourceUrl);

        // 提取描述
        Element descElement = doc.selectFirst("meta[name=description]");
        if (descElement != null) {
            builder.description(descElement.attr("content"));
        } else {
            Element firstP = doc.selectFirst("article p");
            if (firstP != null) {
                builder.description(firstP.text());
            }
        }

        // 尝试提取请求参数
        Elements paramTables = doc.select("table.param-table, table.parameters");
        for (Element table : paramTables) {
            List<com.agent.model.Parameter> params = parseParameterTable(table);
            builder.parameters(params);
        }

        // 尝试提取响应格式
        Element responseSection = doc.selectFirst("code.response, .response-example, #response");
        if (responseSection != null) {
            builder.responseBody(responseSection.text());
        }

        // 检测HTTP方法
        builder.httpMethod(detectHttpMethod(doc.text()));

        // 检测端点
        builder.endpoint(detectEndpoint(doc.text()));

        return builder.build();
    }

    /**
     * 解析参数表格
     */
    private List<com.agent.model.Parameter> parseParameterTable(Element table) {
        List<com.agent.model.Parameter> params = new ArrayList<>();
        Elements rows = table.select("tbody tr");

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() >= 2) {
                String name = cells.get(0).text();
                String type = cells.size() > 1 ? cells.get(1).text() : "string";
                String description = cells.size() > 2 ? cells.get(2).text() : "";

                params.add(new com.agent.model.Parameter(name, type, description, false));
            }
        }

        return params;
    }
}
