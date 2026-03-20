package com.agent.knowledge.v2.builder;

import com.agent.knowledge.v2.model.ScenarioMetadata;
import com.agent.knowledge.v2.model.ScenarioStep;
import com.agent.knowledge.v2.model.TestScenario;
import com.agent.model.ApiMetadata;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 从ApiMetadata自动构建TestScenario
 */
public class ScenarioBuilder {

    private static final int DEFAULT_MAX_STEPS = 5;
    private static final int DEFAULT_MIN_FREQUENCY = 3;

    private int maxSteps;
    private int minFrequency;

    public ScenarioBuilder() {
        this.maxSteps = DEFAULT_MAX_STEPS;
        this.minFrequency = DEFAULT_MIN_FREQUENCY;
    }

    public ScenarioBuilder(int maxSteps, int minFrequency) {
        this.maxSteps = maxSteps;
        this.minFrequency = minFrequency;
    }

    /**
     * 从API列表构建测试场景
     *
     * @param apis ApiMetadata列表
     * @return 生成的TestScenario列表
     */
    public List<TestScenario> buildFromApiGraph(List<ApiMetadata> apis) {
        if (apis == null || apis.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 构建API调用图
        Map<String, Set<String>> callGraph = buildCallGraph(apis);

        // 2. 查找高频路径
        List<List<String>> frequentPaths = findFrequentPaths(callGraph, minFrequency);

        // 3. 转换为TestScenario
        return frequentPaths.stream()
                .map(path -> pathToScenario(path, apis))
                .collect(Collectors.toList());
    }

    /**
     * 构建API调用图（基于描述相似性）
     */
    Map<String, Set<String>> buildCallGraph(List<ApiMetadata> apis) {
        Map<String, Set<String>> graph = new HashMap<>();

        for (ApiMetadata api : apis) {
            graph.put(api.getApiId(), new HashSet<>());
        }

        // 基于描述相似性建立关联
        for (int i = 0; i < apis.size(); i++) {
            for (int j = i + 1; j < apis.size(); j++) {
                ApiMetadata api1 = apis.get(i);
                ApiMetadata api2 = apis.get(j);

                if (isRelated(api1, api2)) {
                    graph.get(api1.getApiId()).add(api2.getApiId());
                    graph.get(api2.getApiId()).add(api1.getApiId());
                }
            }
        }

        return graph;
    }

    /**
     * 判断两个API是否相关
     */
    private boolean isRelated(ApiMetadata api1, ApiMetadata api2) {
        if (api1.getDescription() == null || api2.getDescription() == null) {
            return false;
        }

        String desc1 = api1.getDescription().toLowerCase();
        String desc2 = api2.getDescription().toLowerCase();

        // 检查是否包含相同的关键词
        String[] keywords1 = desc1.split("\\s+");
        String[] keywords2 = desc2.split("\\s+");

        int commonWords = 0;
        for (String word1 : keywords1) {
            for (String word2 : keywords2) {
                if (word1.length() > 3 && word1.equals(word2)) {
                    commonWords++;
                }
            }
        }

        return commonWords >= 2;
    }

    /**
     * 查找高频路径
     */
    List<List<String>> findFrequentPaths(Map<String, Set<String>> callGraph, int minFrequency) {
        List<List<String>> paths = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : callGraph.entrySet()) {
            String startApi = entry.getKey();
            Set<String> neighbors = entry.getValue();

            if (neighbors.size() >= minFrequency) {
                // 创建长度为2的路径
                for (String endApi : neighbors) {
                    if (!startApi.equals(endApi)) {
                        paths.add(Arrays.asList(startApi, endApi));
                    }
                }
            }
        }

        return paths;
    }

    /**
     * 将路径转换为TestScenario
     */
    TestScenario pathToScenario(List<String> apiPath, List<ApiMetadata> apis) {
        Map<String, ApiMetadata> apiMap = apis.stream()
                .collect(Collectors.toMap(ApiMetadata::getApiId, api -> api));

        List<ScenarioStep> steps = new ArrayList<>();
        StringBuilder scenarioName = new StringBuilder();
        StringBuilder scenarioDesc = new StringBuilder();

        for (int i = 0; i < apiPath.size() && i < maxSteps; i++) {
            String apiId = apiPath.get(i);
            ApiMetadata api = apiMap.get(apiId);

            if (api != null) {
                ScenarioStep step = ScenarioStep.builder()
                        .stepOrder(i + 1)
                        .apiId(apiId)
                        .description(api.getDescription())
                        .build();
                steps.add(step);

                if (i > 0) {
                    scenarioName.append(" -> ");
                    scenarioDesc.append("，");
                }
                scenarioName.append(api.getMethodName());
                scenarioDesc.append("执行").append(api.getDescription());
            }
        }

        String scenarioId = "scen_" + UUID.randomUUID().toString().substring(0, 8);

        return TestScenario.builder()
                .scenarioId(scenarioId)
                .name(scenarioName.toString())
                .description(scenarioDesc.toString())
                .steps(steps)
                .metadata(ScenarioMetadata.builder()
                        .serviceName("AutoGenerated")
                        .createdBy("ScenarioBuilder")
                        .build())
                .build();
    }

    /**
     * 从单个API创建简单的单步场景
     */
    public TestScenario buildSingleApiScenario(ApiMetadata api) {
        String scenarioId = "scen_" + UUID.randomUUID().toString().substring(0, 8);

        ScenarioStep step = ScenarioStep.builder()
                .stepOrder(1)
                .apiId(api.getApiId())
                .description(api.getDescription())
                .build();

        return TestScenario.builder()
                .scenarioId(scenarioId)
                .name(api.getMethodName())
                .description("单步场景：" + api.getDescription())
                .addStep(step)
                .metadata(ScenarioMetadata.builder()
                        .serviceName(api.getClassName())
                        .createdBy("ScenarioBuilder")
                        .build())
                .build();
    }

    // Getters and Setters
    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public int getMinFrequency() {
        return minFrequency;
    }

    public void setMinFrequency(int minFrequency) {
        this.minFrequency = minFrequency;
    }
}
