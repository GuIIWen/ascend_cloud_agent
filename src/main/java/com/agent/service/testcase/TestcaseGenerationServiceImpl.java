package com.agent.service.testcase;

import com.agent.crawler.WebDocumentCrawler;
import com.agent.model.ApiMetadata;
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
        List<ApiMetadata> rawKnowledgeBaseHits = List.of();

        if (!hasText(referenceUrl)) {
            rawKnowledgeBaseHits = filterKnowledgeBaseHits(knowledgeBaseService.search(requirement, topK));
            if (rawKnowledgeBaseHits.isEmpty()) {
                throw new TestcaseReferenceUrlRequiredException();
            }
        }

        String refinedRequirement = refineRequirement(requirement);
        List<ApiMetadata> searchResults = knowledgeBaseService.search(refinedRequirement, topK);
        List<ApiMetadata> effectiveKbResults = filterKnowledgeBaseHits(searchResults);
        if (effectiveKbResults.isEmpty() && !rawKnowledgeBaseHits.isEmpty()) {
            effectiveKbResults = rawKnowledgeBaseHits;
        }

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
                expectedErrorCode);
        String javaTestCode = cleanupCodeFence(llmService.generateTestCode(generationPrompt));
        if (!hasText(javaTestCode)) {
            throw new IllegalStateException("LLM returned empty testcase code");
        }
        javaTestCode = generatedTestcasePostProcessor.process(javaTestCode);

        return new TestcaseGenerationResult(javaTestCode, dedupeCitations(citations), degraded);
    }

    private String refineRequirement(String requirement) {
        String prompt = promptBuilder.buildRefinementPrompt(requirement);
        String refined = normalize(llmService.generateTestCode(prompt));
        return hasText(refined) ? refined : requirement;
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
            builder.append("apiId: ").append(nullToEmpty(metadata.getApiId())).append('\n');
            appendLine(builder, "description", metadata.getDescription());
            appendLine(builder, "class", metadata.getClassName());
            appendLine(builder, "method", metadata.getMethodName());
            appendLine(builder, "signature", metadata.getSignature());
            appendLine(builder, "httpMethod", metadata.getHttpMethod());
            appendLine(builder, "endpoint", metadata.getEndpoint());
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

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record ReferenceContext(String content, String source) {
    }
}
