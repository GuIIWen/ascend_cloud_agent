package com.agent.knowledge.v2.retriever;

import com.agent.knowledge.v2.model.TestScenario;
import com.agent.service.EmbeddingService;
import com.agent.service.RerankService;
import com.agent.storage.VectorStoreAdapter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 测试场景检索器 - 支持向量检索和Rerank重排序
 */
public class TestScenarioRetriever {

    private final EmbeddingService embeddingService;
    private final RerankService rerankService;
    private final VectorStoreAdapter vectorStore;

    private int recallTopK;
    private int rerankTopK;

    public TestScenarioRetriever(
            EmbeddingService embeddingService,
            RerankService rerankService,
            VectorStoreAdapter vectorStore) {
        this(embeddingService, rerankService, vectorStore, 20, 5);
    }

    public TestScenarioRetriever(
            EmbeddingService embeddingService,
            RerankService rerankService,
            VectorStoreAdapter vectorStore,
            int recallTopK,
            int rerankTopK) {
        this.embeddingService = embeddingService;
        this.rerankService = rerankService;
        this.vectorStore = vectorStore;
        this.recallTopK = recallTopK;
        this.rerankTopK = rerankTopK;
    }

    /**
     * 检索测试场景
     *
     * @param query 用户查询
     * @param topK 返回结果数量
     * @return 检索到的TestScenario列表
     */
    public List<TestScenario> retrieve(String query, int topK) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 用户查询向量化
        float[] queryVector = embeddingService.embed(query);

        // 2. 向量检索（召回Top N）
        List<EmbeddingMatch<TextSegment>> matches = vectorStore.search(
                Embedding.from(queryVector), recallTopK);

        if (matches.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 提取候选场景及其文本
        List<CandidateScenario> candidates = matches.stream()
                .map(match -> {
                    TestScenario scenario = parseScenarioFromSegment(match.embedded());
                    return new CandidateScenario(scenario, match.embedded().text(), match.score());
                })
                .collect(Collectors.toList());

        // 4. Rerank重排序
        List<String> candidateTexts = candidates.stream()
                .map(CandidateScenario::getText)
                .collect(Collectors.toList());

        List<RerankService.RerankResult> reranked = rerankService.rerank(query, candidateTexts);

        // 5. 返回结果
        return reranked.stream()
                .limit(topK)
                .map(r -> {
                    if (r.getIndex() >= 0 && r.getIndex() < candidates.size()) {
                        return candidates.get(r.getIndex()).getScenario();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 从TextSegment解析TestScenario（需要与存储格式对应）
     */
    private TestScenario parseScenarioFromSegment(TextSegment segment) {
        // 这里需要根据实际的存储格式进行解析
        // 简化实现：假设存储的是JSON格式
        String text = segment.text();
        return TestScenario.builder()
                .name(text)
                .description(text)
                .build();
    }

    /**
     * 候选场景封装
     */
    private static class CandidateScenario {
        private final TestScenario scenario;
        private final String text;
        private final double score;

        public CandidateScenario(TestScenario scenario, String text, double score) {
            this.scenario = scenario;
            this.text = text;
            this.score = score;
        }

        public TestScenario getScenario() {
            return scenario;
        }

        public String getText() {
            return text;
        }

        public double getScore() {
            return score;
        }
    }

    // Getters and Setters
    public int getRecallTopK() {
        return recallTopK;
    }

    public void setRecallTopK(int recallTopK) {
        this.recallTopK = recallTopK;
    }

    public int getRerankTopK() {
        return rerankTopK;
    }

    public void setRerankTopK(int rerankTopK) {
        this.rerankTopK = rerankTopK;
    }
}
