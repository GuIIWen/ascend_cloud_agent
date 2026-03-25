package com.agent.parser;

import com.agent.model.ApiMetadata;
import com.agent.model.DocumentSourceType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static final Pattern ENDPOINT_PATTERN = Pattern.compile("/[a-z0-9_/\\.\\-\\{\\}]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DETAIL_PAGE_PATTERN = Pattern.compile("^/api-modelarts/[A-Za-z0-9][A-Za-z0-9_\\-]*\\.html$");
    private static final Pattern DIRECTORY_PAGE_PATTERN = Pattern.compile("^/api-modelarts/modelarts_\\d+_\\d+\\.html$");

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
     * 从目录页中发现API详情页链接，仅支持一层下钻。
     */
    public List<String> discoverDetailPageUrls(String htmlContent, String sourceUrl) {
        if (htmlContent == null || htmlContent.isBlank() || sourceUrl == null || sourceUrl.isBlank()) {
            return List.of();
        }

        Set<String> urls = new LinkedHashSet<>();
        Document doc = Jsoup.parse(htmlContent, sourceUrl);
        Elements links = doc.select("a[href]");
        String normalizedSourceUrl = normalizeUrl(sourceUrl, sourceUrl);

        for (Element link : links) {
            String href = link.attr("href");
            String detailUrl = normalizeUrl(href, sourceUrl);
            if (detailUrl == null) {
                continue;
            }
            if (detailUrl.equals(normalizedSourceUrl) || !isSupportedDetailPageUrl(detailUrl)) {
                continue;
            }
            urls.add(detailUrl);
        }

        return new ArrayList<>(urls);
    }

    public boolean isDirectoryPage(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return false;
        }

        try {
            String path = URI.create(sourceUrl).getPath();
            return path != null && DIRECTORY_PAGE_PATTERN.matcher(path).matches();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isSupportedDetailPageUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();

            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return false;
            }
            if (host == null || !"support.huaweicloud.com".equalsIgnoreCase(host)) {
                return false;
            }
            if (path == null || !DETAIL_PAGE_PATTERN.matcher(path).matches()) {
                return false;
            }
            return !DIRECTORY_PAGE_PATTERN.matcher(path).matches();
        } catch (Exception e) {
            return false;
        }
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
        if (htmlContent == null || htmlContent.isBlank()) {
            logger.warn("Skip empty Huawei Cloud detail page: {}", sourceUrl);
            return null;
        }

        Document doc = Jsoup.parse(htmlContent, sourceUrl);
        if (!hasMeaningfulContent(doc)) {
            logger.warn("Skip Huawei Cloud detail page without meaningful content: {}", sourceUrl);
            return null;
        }

        String resolvedApiName = resolveApiName(doc, apiName, sourceUrl);

        ApiMetadata.Builder builder = ApiMetadata.builder()
                .apiId(generateApiId(resolvedApiName, sourceUrl))
                .methodName(resolvedApiName)
                .className(extractClassName(resolvedApiName))
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
            } else {
                builder.description(extractApiDescription(doc));
            }
        }

        builder.parameters(extractParameters(doc));

        String requestBody = extractRequestBody(doc);
        if (!requestBody.isBlank()) {
            builder.requestBody(requestBody);
        }

        String responseBody = extractResponseBody(doc);
        if (!responseBody.isBlank()) {
            builder.responseBody(responseBody);
        }

        String uriText = extractUriSectionText(doc);
        builder.httpMethod(extractHttpMethod(uriText.isBlank() ? doc.text() : uriText));
        builder.endpoint(extractEndpoint(uriText.isBlank() ? doc.text() : uriText));

        return builder.build();
    }

    /**
     * 解析参数表格
     */
    private List<com.agent.model.Parameter> parseParameterTable(Element table) {
        List<com.agent.model.Parameter> params = new ArrayList<>();
        Elements rows = table.select("tbody tr");
        if (rows.isEmpty()) {
            rows = table.select("tr");
        }

        List<String> headers = extractTableHeaders(table);

        for (Element row : rows) {
            if (!row.select("th").isEmpty()) {
                continue;
            }
            Elements cells = row.select("td");
            if (cells.size() >= 2) {
                String name = cellText(cells, resolveColumnIndex(headers, List.of("参数", "名称"), 0));
                if (name.isBlank()) {
                    continue;
                }
                String type = cellText(cells, resolveColumnIndex(headers, List.of("参数类型", "类型"), Math.min(1, cells.size() - 1)));
                String description = cellText(cells, resolveColumnIndex(headers, List.of("描述", "说明"), Math.min(2, cells.size() - 1)));
                boolean required = isRequired(cellText(cells, resolveColumnIndex(headers, List.of("是否必选", "是否必填"), -1)));

                params.add(new com.agent.model.Parameter(
                        name,
                        type.isBlank() ? "string" : type,
                        description,
                        required));
            }
        }

        return params;
    }

    private List<String> extractTableHeaders(Element table) {
        List<String> headers = new ArrayList<>();
        Elements headerCells = table.select("thead th");
        if (headerCells.isEmpty()) {
            headerCells = table.select("tr").first() != null ? table.select("tr").first().select("th,td") : new Elements();
        }
        for (Element headerCell : headerCells) {
            headers.add(normalizeWhitespace(headerCell.text()));
        }
        return headers;
    }

    private int resolveColumnIndex(List<String> headers, List<String> candidates, int fallback) {
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            for (String candidate : candidates) {
                if (header.contains(candidate)) {
                    return i;
                }
            }
        }
        return fallback;
    }

    private String cellText(Elements cells, int index) {
        if (index < 0 || index >= cells.size()) {
            return "";
        }
        return normalizeWhitespace(cells.get(index).text());
    }

    private boolean isRequired(String value) {
        String normalized = normalizeWhitespace(value).toLowerCase(Locale.ROOT);
        return "是".equals(normalized)
                || "true".equals(normalized)
                || "y".equals(normalized)
                || "yes".equals(normalized)
                || "required".equals(normalized);
    }

    private String resolveApiName(Document doc, String apiName, String sourceUrl) {
        if (apiName != null && !apiName.isBlank()) {
            return apiName;
        }

        String nameFromUrl = resolveApiNameFromUrl(sourceUrl);
        if (nameFromUrl != null) {
            return nameFromUrl;
        }

        String title = doc.title();
        Matcher matcher = API_NAME_PATTERN.matcher(title);
        if (matcher.find()) {
            return matcher.group();
        }

        return "HuaweiCloudApi";
    }

    private String resolveApiNameFromUrl(String sourceUrl) {
        try {
            String path = URI.create(sourceUrl).getPath();
            String lastSegment = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
            if (!lastSegment.isBlank()) {
                return lastSegment;
            }
        } catch (Exception ignored) {
            // fall back to generic name
        }
        return null;
    }

    private String extractUriSectionText(Document doc) {
        Element uriSection = findSectionByHeading(doc, "URI");
        if (uriSection != null) {
            return normalizeWhitespace(uriSection.text());
        }
        Element uriHeading = findHeading(doc, "URI");
        return uriHeading != null ? collectFollowingSectionText(uriHeading) : "";
    }

    private List<com.agent.model.Parameter> extractParameters(Document doc) {
        List<com.agent.model.Parameter> params = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Element table : doc.select("table")) {
            if (!isParameterTable(table)) {
                continue;
            }
            for (com.agent.model.Parameter parameter : parseParameterTable(table)) {
                String key = parameter.getName() + "|" + parameter.getType();
                if (seen.add(key)) {
                    params.add(parameter);
                }
            }
        }

        return params;
    }

    private boolean isParameterTable(Element table) {
        String context = collectTableContext(table).toLowerCase(Locale.ROOT);
        if (context.contains("响应body参数")
                || context.contains("响应参数")
                || context.contains("response")
                || context.contains("状态码")
                || context.contains("错误码")) {
            return false;
        }
        return context.contains("路径参数")
                || context.contains("请求参数")
                || context.contains("query参数")
                || context.contains("header参数")
                || context.contains("body参数")
                || context.contains("form参数")
                || context.contains("参数");
    }

    private String extractRequestBody(Document doc) {
        Element requestSection = findSectionByHeading(doc, "请求参数");
        if (requestSection == null) {
            return "";
        }

        String text = normalizeWhitespace(requestSection.text());
        if (text.endsWith("无") || "请求参数 无".equals(text)) {
            return "无";
        }

        Element exampleSection = findSectionByHeading(doc, "请求示例");
        String example = extractCodeBlock(exampleSection);
        if (!example.isBlank()) {
            return example;
        }

        String structured = stringifyTables(requestSection.select("table"));
        if (!structured.isBlank()) {
            return structured;
        }

        return text.replaceFirst("^请求参数\\s*", "").trim();
    }

    private String extractResponseBody(Document doc) {
        Element responseExampleSection = findSectionByHeading(doc, "响应示例");
        String example = extractCodeBlock(responseExampleSection);
        if (!example.isBlank()) {
            return example;
        }

        Element responseSection = findSectionByHeading(doc, "响应参数");
        if (responseSection == null) {
            Element responseNode = doc.selectFirst("code.response, .response-example, #response");
            return responseNode != null ? normalizeWhitespace(responseNode.text()) : "";
        }

        String structured = stringifyTables(responseSection.select("table"));
        if (!structured.isBlank()) {
            return structured;
        }

        return normalizeWhitespace(responseSection.text()).replaceFirst("^响应参数\\s*", "").trim();
    }

    private Element findSectionByHeading(Document doc, String headingText) {
        Element heading = findHeading(doc, headingText);
        if (heading == null) {
            return null;
        }
        Element parent = heading.parent();
        if (parent != null && parent.hasClass("section")) {
            return parent;
        }

        Element container = heading;
        while (container.parent() != null && !"body".equals(container.parent().tagName())) {
            Element parentNode = container.parent();
            if (parentNode.select("> h1, > h2, > h3, > h4, > h5, > h6").contains(heading)) {
                return parentNode;
            }
            container = parentNode;
        }
        return heading;
    }

    private Element findHeading(Document doc, String headingText) {
        Elements headings = doc.select("h1, h2, h3, h4, h5, h6, .sectiontitle, dt, strong");
        for (Element heading : headings) {
            if (headingText.equalsIgnoreCase(normalizeWhitespace(heading.text()))) {
                return heading;
            }
        }
        return null;
    }

    private String collectFollowingSectionText(Element heading) {
        StringBuilder sb = new StringBuilder();
        for (Element sibling = heading.nextElementSibling(); sibling != null; sibling = sibling.nextElementSibling()) {
            if (sibling.tagName().matches("h1|h2|h3|h4|h5|h6")
                    || sibling.hasClass("sectiontitle")) {
                break;
            }
            sb.append(sibling.text()).append('\n');
        }
        return normalizeWhitespace(sb.toString());
    }

    private String collectTableContext(Element table) {
        List<String> fragments = new ArrayList<>();
        Element caption = table.selectFirst("caption");
        if (caption != null) {
            fragments.add(caption.text());
        }

        Element parent = table.parent();
        if (parent != null) {
            Element heading = parent.selectFirst("h1, h2, h3, h4, h5, h6, .sectiontitle");
            if (heading != null) {
                fragments.add(heading.text());
            }
        }

        Element previous = table.previousElementSibling();
        int depth = 0;
        while (previous != null && depth < 3) {
            fragments.add(previous.text());
            previous = previous.previousElementSibling();
            depth++;
        }

        return normalizeWhitespace(String.join(" ", fragments));
    }

    private String extractCodeBlock(Element section) {
        if (section == null) {
            return "";
        }
        Element codeBlock = section.selectFirst("pre, code");
        return codeBlock != null ? codeBlock.text().trim() : "";
    }

    private String stringifyTables(Elements tables) {
        StringBuilder sb = new StringBuilder();
        for (Element table : tables) {
            String caption = normalizeWhitespace(table.select("caption").text());
            if (!caption.isBlank()) {
                sb.append(caption).append('\n');
            }
            for (com.agent.model.Parameter parameter : parseParameterTable(table)) {
                sb.append(parameter.getName())
                        .append(" : ")
                        .append(parameter.getType());
                if (!parameter.getDescription().isBlank()) {
                    sb.append(" - ").append(parameter.getDescription());
                }
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean hasMeaningfulContent(Document doc) {
        String title = doc.title() != null ? doc.title().trim() : "";
        String description = "";
        Element descElement = doc.selectFirst("meta[name=description]");
        if (descElement != null) {
            description = descElement.attr("content").trim();
        }
        String uriText = extractUriSectionText(doc).trim();
        String bodyText = doc.body() != null ? doc.body().text().trim() : "";

        return !title.isBlank() || !description.isBlank() || !uriText.isBlank() || !bodyText.isBlank();
    }

    private String normalizeUrl(String href, String sourceUrl) {
        if (href == null) {
            return null;
        }

        String candidate = href.trim();
        if (candidate.isEmpty()
                || candidate.startsWith("#")
                || candidate.toLowerCase().startsWith("javascript:")
                || candidate.toLowerCase().startsWith("mailto:")
                || candidate.toLowerCase().startsWith("tel:")) {
            return null;
        }

        try {
            URI baseUri = URI.create(sourceUrl);
            URI resolvedUri = baseUri.resolve(candidate);
            return new URI(
                    resolvedUri.getScheme(),
                    resolvedUri.getAuthority(),
                    resolvedUri.getPath(),
                    null,
                    null)
                    .normalize()
                    .toString();
        } catch (IllegalArgumentException | URISyntaxException e) {
            logger.debug("Skip invalid Huawei Cloud detail page URL: {} (base: {})", href, sourceUrl, e);
            return null;
        }
    }
}
