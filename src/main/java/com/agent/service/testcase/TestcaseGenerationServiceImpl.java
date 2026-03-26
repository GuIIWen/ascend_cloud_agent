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

/**
 * 测试用例生成主链路实现。
 */
public class TestcaseGenerationServiceImpl implements TestcaseGenerationService {
    private static final Logger logger = LoggerFactory.getLogger(TestcaseGenerationServiceImpl.class);

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_REFERENCE_CONTEXT_CHARS = 8_000;
    private static final int MAX_KB_CONTEXT_RESULTS = 1;
    private static final int MAX_GENERATION_ATTEMPTS = 3;

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
        Integer expectedHttpStatus = request.getExpectedHttpStatus();
        String expectedErrorCode = normalize(request.getExpectedErrorCode());
        String expectedErrorDescription = normalize(request.getExpectedErrorDescription());
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
        String javaTestCode = generateValidatedTestcaseCode(generationPrompt, requirement, refinedRequirement);

        return new TestcaseGenerationResult(
                javaTestCode,
                dedupeCitations(citations),
                degraded,
                refinedRequirement);
    }

    private String generateValidatedTestcaseCode(
            String generationPrompt,
            String requirement,
            String refinedRequirement) {
        String prompt = generationPrompt;
        String lastGenerated = null;
        IllegalStateException lastValidationError = null;

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String javaTestCode = cleanupCodeFence(llmService.generateTestCode(prompt));
            if (!hasText(javaTestCode)) {
                lastValidationError = new IllegalStateException("LLM returned empty testcase code");
            } else {
                try {
                    return generatedTestcasePostProcessor.process(javaTestCode);
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
        appendLine(builder, "description", metadata.getDescription());
        appendLine(builder, "httpMethod", metadata.getHttpMethod());
        appendLine(builder, "endpoint", metadata.getEndpoint());
        appendParameters(builder, metadata.getParameters());
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

    private String alignRefinedRequirement(
            String requirement,
            String refinedRequirement,
            ApiMetadata refinementMetadata,
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        if (!hasExplicitExpectation(expectedHttpStatus, expectedErrorCode, expectedErrorDescription)) {
            return refinedRequirement;
        }
        List<String> segments = new ArrayList<>();
        String goal = hasText(requirement) ? requirement : refinedRequirement;
        if (hasText(goal)) {
            segments.add("目标：" + goal);
        }
        String apiSummary = buildApiSummary(refinementMetadata);
        if (hasText(apiSummary)) {
            segments.add("接口：" + apiSummary);
        }
        String inputSummary = buildParameterSummary(refinementMetadata == null ? null : refinementMetadata.getParameters());
        if (hasText(inputSummary)) {
            segments.add("输入：" + inputSummary);
        }
        segments.add("步骤：使用有效鉴权调用目标接口并记录响应。");
        segments.add("断言：" + buildExpectationSummary(
                expectedHttpStatus,
                expectedErrorCode,
                expectedErrorDescription));
        return String.join(" ", segments);
    }

    private boolean hasExplicitExpectation(
            Integer expectedHttpStatus,
            String expectedErrorCode,
            String expectedErrorDescription) {
        return expectedHttpStatus != null
                || hasText(expectedErrorCode)
                || hasText(expectedErrorDescription);
    }

    private String buildApiSummary(ApiMetadata refinementMetadata) {
        if (refinementMetadata == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (hasText(refinementMetadata.getDescription())) {
            builder.append(refinementMetadata.getDescription().trim());
        }
        if (hasText(refinementMetadata.getHttpMethod()) || hasText(refinementMetadata.getEndpoint())) {
            if (builder.length() > 0) {
                builder.append("，");
            }
            if (hasText(refinementMetadata.getHttpMethod())) {
                builder.append(refinementMetadata.getHttpMethod().trim()).append(" ");
            }
            if (hasText(refinementMetadata.getEndpoint())) {
                builder.append(refinementMetadata.getEndpoint().trim());
            }
        }
        return builder.toString().trim();
    }

    private String buildParameterSummary(List<Parameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Parameter parameter : parameters) {
            if (parameter == null || !hasText(parameter.getName())) {
                continue;
            }
            StringBuilder builder = new StringBuilder(parameter.getName().trim());
            if (hasText(parameter.getDescription())) {
                builder.append("=").append(parameter.getDescription().trim());
            }
            if (parameter.isRequired()) {
                builder.append("（必填）");
            } else {
                builder.append("（选填）");
            }
            parts.add(builder.toString());
        }
        return String.join("；", parts);
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

    private record ReferenceContext(String content, String source) {
    }
}
