package com.agent.service.testcase;

import com.agent.crawler.WebDocumentCrawler;
import com.agent.model.ApiMetadata;
import com.agent.model.Parameter;
import com.agent.service.KnowledgeBaseService;
import com.agent.service.LLMService;
import dev.langchain4j.data.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 测试用例生成主链路实现。
 */
public class TestcaseGenerationServiceImpl implements TestcaseGenerationService {
    private static final Logger logger = LoggerFactory.getLogger(TestcaseGenerationServiceImpl.class);

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_REFERENCE_CONTEXT_CHARS = 8_000;
    private static final int MAX_KB_CONTEXT_RESULTS = 1;
    private static final int MAX_GENERATION_ATTEMPTS = 3;
    private static final Pattern REQUIREMENT_HTTP_STATUS = Pattern.compile(
            "(?:HTTP\\s*(?:状态码|status(?:\\s*code)?)|状态码|返回)\\s*(?:为|是|=|:)?\\s*(\\d{3})(?!\\d)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUIREMENT_ERROR_CODE = Pattern.compile(
            "(?:错误码|error[_\\s-]?code)\\s*(?:为|是|=|:)?\\s*([A-Za-z][A-Za-z0-9_.-]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern KNOWN_ERROR_CODE = Pattern.compile(
            "\\b(?:ModelArts|MODELARTS|APIGW)\\.[A-Za-z0-9]+(?:\\.[A-Za-z0-9]+)?\\b");
    private static final Pattern QUOTED_ERROR_DESCRIPTION = Pattern.compile(
            "(?:错误描述(?:包含)?|error[_\\s-]?(?:msg|message|description))\\s*(?:为|是|=|:|包含)?\\s*[\"“]([^\"”]+)[\"”]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_ERROR_MESSAGE = Pattern.compile(
            "(?:error_msg|error_message)\\s*(?:=|:)\\s*([^,，。；;]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HARDCODED_STATUS_ASSERTION = Pattern.compile(
            "assert(?:Equals|NotEquals)\\s*\\(\\s*\\d{3}\\s*,\\s*[A-Za-z0-9_]+\\.statusCode\\s*\\(");
    private static final Pattern BODY_CONTAINS_ASSERTION = Pattern.compile(
            "assertTrue\\s*\\([^;]*\\.contains\\s*\\(",
            Pattern.DOTALL);
    private static final Pattern WHOLE_BODY_CONTAINS_ASSERTION = Pattern.compile(
            "(?:\\bbody\\b|\\bresponseBody\\b|\\bresponseText\\b|response\\s*\\.\\s*body\\s*\\(\\s*\\))\\s*\\.contains\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUCCESS_OPERATION_FIELD = Pattern.compile(
            "operation_(?:id|status|type)");
    private static final Pattern HTTP_CALL_PATTERN = Pattern.compile(
            "(?:HttpClient\\s*\\.\\s*(?:newBuilder|newHttpClient)\\s*\\(|\\.send(?:Async)?\\s*\\(|OkHttpClient(?:\\.Builder)?\\b|\\.execute\\s*\\()",
            Pattern.DOTALL);
    private static final Pattern HTTP_TIMEOUT_PATTERN = Pattern.compile(
            "\\.(?:timeout|connectTimeout|readTimeout|writeTimeout|callTimeout)\\s*\\(",
            Pattern.DOTALL);

    private final KnowledgeBaseService knowledgeBaseService;
    private final LLMService llmService;
    private final WebDocumentCrawler webDocumentCrawler;
    private final TestcasePromptBuilder promptBuilder;
    private final GeneratedTestcasePostProcessor generatedTestcasePostProcessor;
    private final int topK;

    public TestcaseGenerationServiceImpl(
            KnowledgeBaseService knowledgeBaseService,
            LLMService llmService,
            WebDocumentCrawler webDocumentCrawler) {
        this(
                knowledgeBaseService,
                llmService,
                webDocumentCrawler,
                new TestcasePromptBuilder(),
                new GeneratedTestcasePostProcessor(),
                DEFAULT_TOP_K);
    }

    public TestcaseGenerationServiceImpl(
            KnowledgeBaseService knowledgeBaseService,
            LLMService llmService,
            WebDocumentCrawler webDocumentCrawler,
            int topK) {
        this(
                knowledgeBaseService,
                llmService,
                webDocumentCrawler,
                new TestcasePromptBuilder(),
                new GeneratedTestcasePostProcessor(),
                topK);
    }

    TestcaseGenerationServiceImpl(
            KnowledgeBaseService knowledgeBaseService,
            LLMService llmService,
            WebDocumentCrawler webDocumentCrawler,
            TestcasePromptBuilder promptBuilder,
            GeneratedTestcasePostProcessor generatedTestcasePostProcessor,
            int topK) {
        this.knowledgeBaseService = Objects.requireNonNull(knowledgeBaseService, "knowledgeBaseService");
        this.llmService = Objects.requireNonNull(llmService, "llmService");
        this.webDocumentCrawler = Objects.requireNonNull(webDocumentCrawler, "webDocumentCrawler");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
        this.generatedTestcasePostProcessor =
                Objects.requireNonNull(generatedTestcasePostProcessor, "generatedTestcasePostProcessor");
        this.topK = topK > 0 ? topK : DEFAULT_TOP_K;
    }

    @Override
    public TestcaseGenerationResult generate(TestcaseGenerationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        String requirement = normalize(request.getRequirement());
        if (!hasText(requirement)) {
            throw new IllegalArgumentException("requirement must not be blank");
        }
        String referenceUrl = normalize(request.getReferenceUrl());
        ResolvedExpectation resolvedExpectation = resolveExpectation(
                requirement,
                request.getExpectedHttpStatus(),
                normalize(request.getExpectedErrorCode()),
                normalize(request.getExpectedErrorDescription()));
        Integer expectedHttpStatus = resolvedExpectation.expectedHttpStatus();
        String expectedErrorCode = resolvedExpectation.expectedErrorCode();
        String expectedErrorDescription = resolvedExpectation.expectedErrorDescription();
        List<ApiMetadata> rawKnowledgeBaseHits = List.of();

        if (!hasText(referenceUrl)) {
            rawKnowledgeBaseHits = filterKnowledgeBaseHits(knowledgeBaseService.search(requirement, topK));
            if (rawKnowledgeBaseHits.isEmpty()) {
                throw new TestcaseReferenceUrlRequiredException();
            }
        }

        ApiMetadata refinementMetadata = limitKnowledgeBaseHits(rawKnowledgeBaseHits).stream()
                .findFirst()
                .orElse(null);
        String refinementAnchor = buildRefinementAnchor(refinementMetadata);
        String refinedRequirement = refineRequirement(
                requirement,
                refinementMetadata,
                refinementAnchor,
                expectedHttpStatus,
                expectedErrorCode,
                expectedErrorDescription);
        List<ApiMetadata> searchResults = knowledgeBaseService.search(refinedRequirement, topK);
        List<ApiMetadata> effectiveKbResults = filterKnowledgeBaseHits(searchResults);
        if (effectiveKbResults.isEmpty() && !rawKnowledgeBaseHits.isEmpty()) {
            effectiveKbResults = rawKnowledgeBaseHits;
        }
        effectiveKbResults = limitKnowledgeBaseHits(effectiveKbResults);

        boolean degraded;
        String context;
        List<TestcaseCitation> citations = new ArrayList<>();
        if (!effectiveKbResults.isEmpty()) {
            degraded = false;
            context = buildKnowledgeBaseContext(effectiveKbResults);
            citations.addAll(buildKnowledgeBaseCitations(effectiveKbResults));
        } else {
            if (!hasText(referenceUrl)) {
                throw new TestcaseReferenceUrlRequiredException();
            }
            degraded = true;
            ReferenceContext referenceContext = fetchReferenceContext(referenceUrl);
            context = referenceContext.content();
            citations.add(TestcaseCitation.referenceUrl(referenceContext.source()));
        }

        String generationPrompt = promptBuilder.buildCodeGenerationPrompt(
                refinedRequirement,
                context,
                !effectiveKbResults.isEmpty(),
                expectedHttpStatus,
                expectedErrorCode,
                expectedErrorDescription);
        ApiMetadata primaryGenerationMetadata = effectiveKbResults.stream().findFirst().orElse(null);
        String javaTestCode = generateValidatedTestcaseCode(
                generationPrompt,
                requirement,
                refinedRequirement,
                primaryGenerationMetadata,
                expectedHttpStatus,
                expectedErrorCode,
                expectedErrorDescription);

        return new TestcaseGenerationResult(
                javaTestCode,
                dedupeCitations(citations),
                degraded,
                refinedRequirement);
    }

    private String generateValidatedTestcaseCode(
            String generationPrompt,
            String requirement,
            String refinedRequirement,
            ApiMetadata primaryMetadata,
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        String prompt = generationPrompt;
        String lastGenerated = null;
        IllegalStateException lastValidationError = null;

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String javaTestCode = cleanupCodeFence(llmService.generateTestCode(prompt));
            if (!hasText(javaTestCode)) {
                lastValidationError = new IllegalStateException("LLM returned empty testcase code");
            } else {
                try {
                    String normalizedCode = generatedTestcasePostProcessor.process(javaTestCode);
                    validateGeneratedOutput(
                            normalizedCode,
                            primaryMetadata,
                            expectedHttpStatus,
                            expectedErrorCode,
                            expectedErrorDescription);
                    return normalizedCode;
                } catch (IllegalStateException e) {
                    lastGenerated = javaTestCode;
                    lastValidationError = e;
                    logger.warn(
                            "Generated testcase validation failed on attempt {}/{}: requirement='{}' refinedRequirement='{}' reason='{}'",
                            attempt,
                            MAX_GENERATION_ATTEMPTS,
                            requirement,
                            refinedRequirement,
                            e.getMessage());
                }
            }

            if (attempt < MAX_GENERATION_ATTEMPTS) {
                prompt = promptBuilder.buildRetryGenerationPrompt(
                        generationPrompt,
                        lastValidationError.getMessage(),
                        attempt + 1);
            }
        }

        logger.error(
                "Generated testcase post-processing failed after {} attempts: requirement='{}' refinedRequirement='{}' preview='{}'",
                MAX_GENERATION_ATTEMPTS,
                requirement,
                refinedRequirement,
                abbreviate(lastGenerated),
                lastValidationError);
        throw lastValidationError;
    }

    private String refineRequirement(
            String requirement,
            ApiMetadata refinementMetadata,
            String refinementAnchor,
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        String prompt = promptBuilder.buildRefinementPrompt(
                requirement,
                refinementAnchor,
                expectedHttpStatus,
                expectedErrorCode,
                expectedErrorDescription);
        String refined = normalize(llmService.generateTestCode(prompt));
        String effective = hasText(refined) ? refined : requirement;
        return alignRefinedRequirement(
                requirement,
                effective,
                refinementMetadata,
                expectedHttpStatus,
                expectedErrorCode,
                expectedErrorDescription);
    }

    private List<ApiMetadata> filterKnowledgeBaseHits(List<ApiMetadata> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream().filter(this::isConcreteKnowledgeHit).toList();
    }

    private boolean isConcreteKnowledgeHit(ApiMetadata metadata) {
        if (metadata == null || !hasText(metadata.getApiId())) {
            return false;
        }
        return hasText(metadata.getClassName())
                || hasText(metadata.getMethodName())
                || hasText(metadata.getSignature())
                || hasText(metadata.getEndpoint())
                || hasText(metadata.getHttpMethod())
                || hasText(metadata.getRequestBody())
                || hasText(metadata.getResponseBody());
    }

    private String buildKnowledgeBaseContext(List<ApiMetadata> metadataList) {
        StringBuilder builder = new StringBuilder();
        for (ApiMetadata metadata : metadataList) {
            builder.append("allowedApiId: ").append(nullToEmpty(metadata.getApiId())).append('\n');
            builder.append("allowedKnowledgeSource: knowledge-base-top-hit").append('\n');
            builder.append("guardrail: use only this allowed API identity, method, endpoint, source, and parameters; do not invent a second API").append('\n');
            builder.append("guardrail: if explicit expectedHttpStatus/expectedErrorCode/expectedErrorDescription are not provided elsewhere in the prompt and not present below, do not fabricate them").append('\n');
            appendLine(builder, "description", metadata.getDescription());
            appendLine(builder, "class", metadata.getClassName());
            appendLine(builder, "method", metadata.getMethodName());
            appendLine(builder, "signature", metadata.getSignature());
            appendLine(builder, "httpMethod", metadata.getHttpMethod());
            appendLine(builder, "endpoint", metadata.getEndpoint());
            appendParameters(builder, metadata.getParameters());
            appendPathParamBindings(builder, metadata);
            appendLine(builder, "requestBody", metadata.getRequestBody());
            appendLine(builder, "responseBody", metadata.getResponseBody());
            appendLine(builder, "source", metadata.getSourceLocation());
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private List<TestcaseCitation> buildKnowledgeBaseCitations(List<ApiMetadata> metadataList) {
        List<TestcaseCitation> citations = new ArrayList<>();
        for (ApiMetadata metadata : metadataList) {
            citations.add(TestcaseCitation.knowledgeBase(metadata.getApiId(), normalize(metadata.getSourceLocation())));
        }
        return citations;
    }

    private List<ApiMetadata> limitKnowledgeBaseHits(List<ApiMetadata> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .limit(MAX_KB_CONTEXT_RESULTS)
                .toList();
    }

    private String buildRefinementAnchor(ApiMetadata metadata) {
        if (metadata == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "apiId", metadata.getApiId());
        appendLine(builder, "httpMethod", metadata.getHttpMethod());
        appendLine(builder, "endpoint", metadata.getEndpoint());
        appendRefinementParameterNames(builder, metadata.getParameters());
        appendLine(builder, "source", metadata.getSourceLocation());
        return builder.toString().trim();
    }

    private ReferenceContext fetchReferenceContext(String referenceUrl) {
        try {
            Document document = webDocumentCrawler.crawl(referenceUrl);
            if (document == null) {
                throw new TestcaseReferenceFetchException("referenceUrl content is empty: " + referenceUrl);
            }
            String content = normalize(document.text());
            if (!hasText(content)) {
                throw new TestcaseReferenceFetchException("referenceUrl content is empty: " + referenceUrl);
            }
            if (content.length() > MAX_REFERENCE_CONTEXT_CHARS) {
                content = content.substring(0, MAX_REFERENCE_CONTEXT_CHARS);
            }

            String source = normalize(document.metadata("source"));
            String title = normalize(document.metadata("title"));
            StringBuilder builder = new StringBuilder();
            if (hasText(title)) {
                builder.append("title: ").append(title).append('\n');
            }
            builder.append("source: ").append(hasText(source) ? source : referenceUrl).append('\n');
            builder.append("content:\n").append(content);
            return new ReferenceContext(builder.toString(), hasText(source) ? source : referenceUrl);
        } catch (IOException e) {
            logger.error("Failed to crawl referenceUrl='{}'", referenceUrl, e);
            throw new TestcaseReferenceFetchException("Failed to fetch referenceUrl: " + referenceUrl, e);
        }
    }

    private List<TestcaseCitation> dedupeCitations(List<TestcaseCitation> citations) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        Set<TestcaseCitation> deduped = new LinkedHashSet<>(citations);
        return new ArrayList<>(deduped);
    }

    private void appendLine(StringBuilder builder, String name, String value) {
        if (!hasText(value)) {
            return;
        }
        builder.append(name).append(": ").append(value.trim()).append('\n');
    }

    private void appendParameters(StringBuilder builder, List<Parameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        builder.append("parameters:").append('\n');
        for (Parameter parameter : parameters) {
            if (parameter == null || !hasText(parameter.getName())) {
                continue;
            }
            builder.append("- name=").append(parameter.getName().trim());
            if (hasText(parameter.getType())) {
                builder.append(", type=").append(parameter.getType().trim());
            }
            builder.append(", required=").append(parameter.isRequired());
            if (hasText(parameter.getDescription())) {
                builder.append(", description=").append(parameter.getDescription().trim());
            }
            builder.append('\n');
        }
    }

    private void appendRefinementParameterNames(StringBuilder builder, List<Parameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (Parameter parameter : parameters) {
            if (parameter == null || !hasText(parameter.getName())) {
                continue;
            }
            names.add(parameter.getName().trim());
        }
        if (!names.isEmpty()) {
            builder.append("parameterNames: ").append(String.join("、", names)).append('\n');
        }
    }

    private void appendPathParamBindings(StringBuilder builder, ApiMetadata metadata) {
        if (metadata == null || !hasText(metadata.getEndpoint())) {
            return;
        }
        Set<String> bindings = new LinkedHashSet<>();
        String endpoint = metadata.getEndpoint();
        if (endpoint.contains("{project_id}")) {
            bindings.add("pathParamBinding: project_id -> PROJECT_ID");
        }
        if (endpoint.contains("/dev-servers/{id}")) {
            bindings.add("pathParamBinding: id -> DEV_SERVER_ID");
        }
        if (endpoint.contains("{server_id}")) {
            bindings.add("pathParamBinding: server_id -> SERVER_ID");
        }
        if (endpoint.contains("{instance_id}")) {
            bindings.add("pathParamBinding: instance_id -> INSTANCE_ID");
        }
        if (endpoint.contains("{volume_id}")) {
            bindings.add("pathParamBinding: volume_id -> VOLUME_ID");
        }
        if (endpoint.contains("{disk_id}")) {
            bindings.add("pathParamBinding: disk_id -> DISK_ID");
        }
        bindings.forEach(binding -> builder.append(binding).append('\n'));
    }

    private String alignRefinedRequirement(
            String requirement,
            String refinedRequirement,
            ApiMetadata refinementMetadata,
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        if (!shouldNormalizeRefinedRequirement(refinementMetadata, expectedHttpStatus, expectedErrorCode, expectedErrorDescription)) {
            return refinedRequirement;
        }
        List<String> segments = new ArrayList<>();
        segments.add("前置条件：" + resolvePrecondition(refinedRequirement));
        segments.add("输入：" + buildCompactParameterSummary(refinementMetadata == null ? null : refinementMetadata.getParameters()));
        segments.add("步骤：" + buildStepSummary(refinementMetadata));
        segments.add("断言：" + buildExpectationSummary(
                expectedHttpStatus,
                expectedErrorCode,
                expectedErrorDescription));
        return String.join("\n", segments);
    }

    private boolean shouldNormalizeRefinedRequirement(
            ApiMetadata refinementMetadata,
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        if (hasExplicitExpectation(expectedHttpStatus, expectedErrorCode, expectedErrorDescription)) {
            return true;
        }
        return refinementMetadata != null
                && (hasText(refinementMetadata.getEndpoint())
                || (refinementMetadata.getParameters() != null && !refinementMetadata.getParameters().isEmpty()));
    }

    private boolean hasExplicitExpectation(
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        return expectedHttpStatus != null
                || hasText(expectedErrorCode)
                || hasText(expectedErrorDescription);
    }

    private String resolvePrecondition(String refinedRequirement) {
        String extracted = extractSegment(refinedRequirement, "前置条件：");
        if (!hasText(extracted)) {
            extracted = extractSegment(refinedRequirement, "前提条件：");
        }
        return hasText(extracted) ? extracted : "待确认";
    }

    private String buildCompactParameterSummary(List<Parameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "待确认";
        }
        List<String> parts = new ArrayList<>();
        for (Parameter parameter : parameters) {
            if (parameter == null || !hasText(parameter.getName())) {
                continue;
            }
            parts.add(parameter.getName().trim());
        }
        return parts.isEmpty() ? "待确认" : String.join("、", parts);
    }

    private String buildStepSummary(ApiMetadata refinementMetadata) {
        if (refinementMetadata == null) {
            return "调用目标接口。";
        }
        StringBuilder builder = new StringBuilder("调用");
        if (hasText(refinementMetadata.getHttpMethod())) {
            builder.append(" ").append(refinementMetadata.getHttpMethod().trim());
        }
        if (hasText(refinementMetadata.getEndpoint())) {
            builder.append(" ").append(refinementMetadata.getEndpoint().trim());
        } else {
            builder.append(" 目标接口");
        }
        builder.append(" 接口。");
        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    private String buildExpectationSummary(
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        List<String> parts = new ArrayList<>();
        if (expectedHttpStatus != null) {
            parts.add("HTTP状态码=" + expectedHttpStatus);
        }
        if (hasText(expectedErrorCode)) {
            parts.add("错误码=" + expectedErrorCode);
        }
        if (hasText(expectedErrorDescription)) {
            parts.add("错误描述包含\"" + expectedErrorDescription + "\"");
        }
        if (parts.isEmpty()) {
            return "待确认";
        }
        return String.join("，", parts);
    }

    private void validateGeneratedOutput(
            String javaTestCode,
            ApiMetadata primaryMetadata,
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        validatePathBinding(javaTestCode, primaryMetadata);
        validateHttpTimeout(javaTestCode);
        validateExplicitExpectationCoverage(
                javaTestCode,
                expectedHttpStatus,
                expectedErrorCode,
                expectedErrorDescription);
        validateErrorAssertionStyle(javaTestCode, expectedErrorCode, expectedErrorDescription);
        if (isNegativeExpectation(expectedHttpStatus, expectedErrorCode, expectedErrorDescription)
                && SUCCESS_OPERATION_FIELD.matcher(javaTestCode).find()) {
            throw new IllegalStateException(
                    "Generated negative testcase code must not assert success response fields such as operation_id/operation_status/operation_type");
        }
        if (hasExplicitExpectation(expectedHttpStatus, expectedErrorCode, expectedErrorDescription)) {
            return;
        }
        if (HARDCODED_STATUS_ASSERTION.matcher(javaTestCode).find()) {
            throw new IllegalStateException(
                    "Generated testcase code must not hard-code HTTP status assertions when explicit expectation is absent");
        }
        if (BODY_CONTAINS_ASSERTION.matcher(javaTestCode).find()) {
            throw new IllegalStateException(
                    "Generated testcase code must not assert response body fields when response truth is absent");
        }
    }

    private void validateExplicitExpectationCoverage(
            String javaTestCode,
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        if (expectedHttpStatus != null && !hasStatusAssertion(javaTestCode, expectedHttpStatus)) {
            throw new IllegalStateException(
                    "Generated testcase code must assert HTTP status "
                            + expectedHttpStatus
                            + " via assertEquals("
                            + expectedHttpStatus
                            + ", response.statusCode()) or equivalent");
        }
        if (hasText(expectedErrorCode) && !javaTestCode.contains(expectedErrorCode)) {
            throw new IllegalStateException(
                    "Generated testcase code must assert expected error code " + expectedErrorCode);
        }
        if (hasText(expectedErrorDescription) && !javaTestCode.contains(expectedErrorDescription)) {
            throw new IllegalStateException(
                    "Generated testcase code must assert expected error description " + expectedErrorDescription);
        }
    }

    private void validateHttpTimeout(String javaTestCode) {
        if (!HTTP_CALL_PATTERN.matcher(javaTestCode).find()) {
            return;
        }
        if (!HTTP_TIMEOUT_PATTERN.matcher(javaTestCode).find()) {
            throw new IllegalStateException(
                    "Generated testcase code must configure HTTP timeout for real HTTP calls");
        }
    }

    private void validateErrorAssertionStyle(
            String javaTestCode,
            String expectedErrorCode,
            String expectedErrorDescription) {
        if (!hasText(expectedErrorCode) && !hasText(expectedErrorDescription)) {
            return;
        }
        if (WHOLE_BODY_CONTAINS_ASSERTION.matcher(javaTestCode).find()) {
            throw new IllegalStateException(
                    "Generated testcase code must not assert explicit error code/description via whole-body contains; parse errorCode/errorDescription into variables and assert those variables instead");
        }
    }

    private boolean isNegativeExpectation(
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        return (expectedHttpStatus != null && expectedHttpStatus >= 400)
                || hasText(expectedErrorCode)
                || hasText(expectedErrorDescription);
    }

    private boolean hasStatusAssertion(String javaTestCode, int expectedHttpStatus) {
        String status = Integer.toString(expectedHttpStatus);
        Pattern assertEqualsStatusCode = Pattern.compile(
                "assert(?:Equals|NotEquals)\\s*\\(\\s*" + status + "\\s*,\\s*[^;\\n]*statusCode\\s*\\(");
        Pattern assertEqualsStatusCodeReversed = Pattern.compile(
                "assert(?:Equals|NotEquals)\\s*\\(\\s*[^;\\n]*statusCode\\s*\\(\\s*\\)\\s*,\\s*" + status + "\\s*\\)");
        Pattern assertTrueStatusCode = Pattern.compile(
                "assertTrue\\s*\\([^;\\n]*statusCode\\s*\\(\\s*\\)\\s*==\\s*" + status + "[^;\\n]*\\)");
        return assertEqualsStatusCode.matcher(javaTestCode).find()
                || assertEqualsStatusCodeReversed.matcher(javaTestCode).find()
                || assertTrueStatusCode.matcher(javaTestCode).find();
    }

    private void validatePathBinding(String javaTestCode, ApiMetadata primaryMetadata) {
        if (primaryMetadata == null || !hasText(primaryMetadata.getEndpoint())) {
            return;
        }
        String endpoint = primaryMetadata.getEndpoint();
        if (endpoint.contains("/dev-servers/")
                && (javaTestCode.contains("HUAWEICLOUD_SERVER_ID")
                || javaTestCode.contains("hwcloud.server.id"))) {
            throw new IllegalStateException(
                    "Generated testcase code must use DEV_SERVER_ID binding for /dev-servers/{id} endpoints");
        }
    }

    private String extractSegment(String text, String prefix) {
        if (!hasText(text) || !hasText(prefix)) {
            return "";
        }
        int start = text.indexOf(prefix);
        if (start < 0) {
            return "";
        }
        start += prefix.length();
        int end = text.length();
        int newline = text.indexOf('\n', start);
        if (newline >= 0) {
            end = Math.min(end, newline);
        }
        for (String marker : List.of("前置条件：", "前提条件：", "输入：", "步骤：", "断言：")) {
            if (marker.equals(prefix)) {
                continue;
            }
            int markerIndex = text.indexOf(marker, start);
            if (markerIndex >= 0) {
                end = Math.min(end, markerIndex);
            }
        }
        String segment = text.substring(start, end);
        return segment.trim();
    }

    private String cleanupCodeFence(String generated) {
        String text = normalize(generated);
        if (!hasText(text)) {
            return text;
        }

        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd >= 0) {
                text = text.substring(firstLineEnd + 1).trim();
            }
            int lastFence = text.lastIndexOf("```");
            if (lastFence >= 0) {
                text = text.substring(0, lastFence).trim();
            }
        }
        return text;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String abbreviate(String value) {
        if (!hasText(value)) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 240) + "...";
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private ResolvedExpectation resolveExpectation(
            String requirement,
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        Integer resolvedHttpStatus = expectedHttpStatus != null
                ? expectedHttpStatus
                : extractHttpStatus(requirement);
        String resolvedErrorCode = hasText(expectedErrorCode)
                ? expectedErrorCode
                : extractErrorCode(requirement);
        String resolvedErrorDescription = hasText(expectedErrorDescription)
                ? expectedErrorDescription
                : extractErrorDescription(requirement);
        return new ResolvedExpectation(
                resolvedHttpStatus,
                resolvedErrorCode,
                resolvedErrorDescription);
    }

    private Integer extractHttpStatus(String requirement) {
        if (!hasText(requirement)) {
            return null;
        }
        var matcher = REQUIREMENT_HTTP_STATUS.matcher(requirement);
        if (!matcher.find()) {
            return null;
        }
        int status = Integer.parseInt(matcher.group(1));
        return status >= 100 && status <= 599 ? status : null;
    }

    private String extractErrorCode(String requirement) {
        if (!hasText(requirement)) {
            return null;
        }
        var labeledMatcher = REQUIREMENT_ERROR_CODE.matcher(requirement);
        if (labeledMatcher.find()) {
            return normalize(labeledMatcher.group(1));
        }
        var knownMatcher = KNOWN_ERROR_CODE.matcher(requirement);
        if (knownMatcher.find()) {
            return normalize(knownMatcher.group());
        }
        return null;
    }

    private String extractErrorDescription(String requirement) {
        if (!hasText(requirement)) {
            return null;
        }
        var quotedMatcher = QUOTED_ERROR_DESCRIPTION.matcher(requirement);
        if (quotedMatcher.find()) {
            return normalize(quotedMatcher.group(1));
        }
        var rawMatcher = RAW_ERROR_MESSAGE.matcher(requirement);
        if (rawMatcher.find()) {
            return normalize(rawMatcher.group(1));
        }
        return null;
    }

    private record ReferenceContext(String content, String source) {
    }

    private record ResolvedExpectation(
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
    }
}
