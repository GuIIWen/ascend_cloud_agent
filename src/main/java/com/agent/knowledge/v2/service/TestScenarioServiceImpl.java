package com.agent.knowledge.v2.service;

import com.agent.knowledge.v2.exception.ScenarioNotFoundException;
import com.agent.knowledge.v2.model.ScenarioMetadata;
import com.agent.knowledge.v2.model.TestScenario;
import com.agent.knowledge.v2.retriever.TestScenarioRetriever;
import com.agent.service.EmbeddingService;
import com.agent.storage.MetadataStore;
import com.agent.storage.VectorStoreAdapter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 测试场景服务实现
 */
public class TestScenarioServiceImpl implements TestScenarioService {

    private final VectorStoreAdapter vectorStore;
    private final EmbeddingService embeddingService;
    private final MetadataStore metadataStore;
    private final TestScenarioRetriever retriever;

    // 内存存储，用于快速访问
    private final Map<String, TestScenario> scenarioCache;

    public TestScenarioServiceImpl(
            VectorStoreAdapter vectorStore,
            EmbeddingService embeddingService,
            MetadataStore metadataStore) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.metadataStore = metadataStore;
        this.retriever = new TestScenarioRetriever(embeddingService, null, vectorStore);
        this.scenarioCache = new ConcurrentHashMap<>();
    }

    public TestScenarioServiceImpl(
            VectorStoreAdapter vectorStore,
            EmbeddingService embeddingService,
            MetadataStore metadataStore,
            TestScenarioRetriever retriever) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.metadataStore = metadataStore;
        this.retriever = retriever;
        this.scenarioCache = new ConcurrentHashMap<>();
    }

    @Override
    public IndexStats buildScenarioIndex(List<TestScenario> scenarios) {
        long startTime = System.currentTimeMillis();
        int total = scenarios != null ? scenarios.size() : 0;
        int success = 0;
        int failure = 0;

        if (scenarios == null || scenarios.isEmpty()) {
            return new IndexStats(0, 0, 0, System.currentTimeMillis() - startTime);
        }

        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();

        for (TestScenario scenario : scenarios) {
            try {
                // 缓存场景
                scenarioCache.put(scenario.getScenarioId(), scenario);

                // 生成向量
                String text = scenario.toText();
                float[] vector = embeddingService.embed(text);

                // 创建文本段
                TextSegment segment = TextSegment.from(text);
                segment.metadata().put("scenarioId", scenario.getScenarioId());

                embeddings.add(Embedding.from(vector));
                segments.add(segment);

                success++;
            } catch (Exception e) {
                failure++;
            }
        }

        // 批量添加到向量存储
        if (!embeddings.isEmpty()) {
            vectorStore.addAll(embeddings, segments);
        }

        long duration = System.currentTimeMillis() - startTime;
        return new IndexStats(total, success, failure, duration);
    }

    @Override
    public List<TestScenario> searchScenarios(String query, int topK) {
        return retriever.retrieve(query, topK);
    }

    @Override
    public List<TestScenario> findByApiId(String apiId) {
        return scenarioCache.values().stream()
                .filter(scenario -> scenario.getSteps() != null)
                .filter(scenario -> scenario.getSteps().stream()
                        .anyMatch(step -> apiId.equals(step.getApiId())))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<TestScenario> getScenarioById(String scenarioId) {
        TestScenario scenario = scenarioCache.get(scenarioId);
        return Optional.ofNullable(scenario);
    }

    @Override
    public void updateScenario(TestScenario scenario) {
        if (scenario == null || scenario.getScenarioId() == null) {
            throw new IllegalArgumentException("Scenario or scenarioId cannot be null");
        }

        if (!scenarioCache.containsKey(scenario.getScenarioId())) {
            throw new ScenarioNotFoundException(scenario.getScenarioId());
        }

        // 更新元数据
        ScenarioMetadata metadata = scenario.getMetadata();
        if (metadata != null) {
            metadata.touch();
        }

        // 更新缓存
        scenarioCache.put(scenario.getScenarioId(), scenario);
    }

    @Override
    public void deleteScenario(String scenarioId) {
        if (scenarioId == null) {
            throw new IllegalArgumentException("scenarioId cannot be null");
        }

        if (!scenarioCache.containsKey(scenarioId)) {
            throw new ScenarioNotFoundException(scenarioId);
        }

        scenarioCache.remove(scenarioId);
    }

    /**
     * 获取缓存中的所有场景
     */
    public Collection<TestScenario> getAllScenarios() {
        return scenarioCache.values();
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return scenarioCache.size();
    }
}
