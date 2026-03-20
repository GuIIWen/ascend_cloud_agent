package com.agent.knowledge.v2.builder;

import com.agent.knowledge.v2.model.ScenarioMetadata;
import com.agent.knowledge.v2.model.ScenarioStep;
import com.agent.knowledge.v2.model.TestScenario;
import com.agent.knowledge.v2.model.Validation;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 从YAML配置文件加载场景
 */
public class YamlScenarioLoader {

    private final ScenarioBuilder scenarioBuilder;

    public YamlScenarioLoader() {
        this.scenarioBuilder = new ScenarioBuilder();
    }

    public YamlScenarioLoader(ScenarioBuilder scenarioBuilder) {
        this.scenarioBuilder = scenarioBuilder;
    }

    /**
     * 从YAML文件加载场景列表
     *
     * @param filePath YAML文件路径
     * @return TestScenario列表
     * @throws IOException 如果文件读取失败
     */
    public List<TestScenario> loadFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        String content = new String(Files.readAllBytes(path));
        return parseYaml(content);
    }

    /**
     * 解析YAML内容
     */
    List<TestScenario> parseYaml(String yamlContent) {
        List<TestScenario> scenarios = new ArrayList<>();
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            return scenarios;
        }

        Object loaded = new Yaml().load(yamlContent);
        if (!(loaded instanceof Map)) {
            return scenarios;
        }

        Map<String, Object> yaml = castMap(loaded);
        Object scenariosObj = yaml.get("scenarios");
        if (scenariosObj == null) {
            scenariosObj = yaml.get("scenario");
        }

        if (scenariosObj instanceof List) {
            List<Map<String, Object>> scenarioList = castListOfMaps(scenariosObj);
            for (Map<String, Object> scenarioData : scenarioList) {
                TestScenario scenario = parseScenario(scenarioData);
                if (scenario != null) {
                    scenarios.add(scenario);
                }
            }
        } else if (scenariosObj instanceof Map) {
            // 单个场景
            TestScenario scenario = parseScenario(castMap(scenariosObj));
            if (scenario != null) {
                scenarios.add(scenario);
            }
        }

        return scenarios;
    }

    /**
     * 解析单个场景
     */
    private TestScenario parseScenario(Map<String, Object> data) {
        try {
            validateRequiredFields(data);

            TestScenario.Builder builder = TestScenario.builder()
                    .scenarioId(getString(data, "id"))
                    .name(getString(data, "name"))
                    .description(getString(data, "description"));

            ScenarioMetadata metadata = buildMetadata(data);
            if (metadata != null) {
                builder.metadata(metadata);
            }

            // 解析步骤
            Object stepsObj = data.get("steps");
            if (stepsObj instanceof List) {
                List<Map<String, Object>> stepsData = castListOfMaps(stepsObj);
                for (Map<String, Object> stepData : stepsData) {
                    ScenarioStep step = parseStep(stepData);
                    if (step != null) {
                        builder.addStep(step);
                    }
                }
            }

            // 解析验证点
            Object validationsObj = data.get("validations");
            if (validationsObj instanceof List) {
                List<Map<String, Object>> validationsData = castListOfMaps(validationsObj);
                for (Map<String, Object> validationData : validationsData) {
                    Validation validation = parseValidation(validationData);
                    if (validation != null) {
                        builder.addValidation(validation);
                    }
                }
            }

            return builder.build();
        } catch (Exception e) {
            // 记录日志并跳过无效场景
            System.err.println("Failed to parse scenario: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析步骤
     */
    private ScenarioStep parseStep(Map<String, Object> data) {
        try {
            ScenarioStep.Builder builder = ScenarioStep.builder()
                    .stepOrder(getInt(data, "order", 0))
                    .apiId(getString(data, "apiId"))
                    .description(getString(data, "description"));

            // 解析输入参数
            Object inputParamsObj = data.get("inputParams");
            if (inputParamsObj instanceof Map) {
                Map<String, Object> inputParams = castMap(inputParamsObj);
                for (Map.Entry<String, Object> entry : inputParams.entrySet()) {
                    builder.addInputParam(entry.getKey(), entry.getValue());
                }
            }

            // 解析上一步参数提取
            Object paramExtractObj = data.get("paramExtractFromPrev");
            if (paramExtractObj instanceof Map) {
                builder.paramExtractFromPrev(joinKeyValuePairs(castMap(paramExtractObj)));
            }

            // 解析输出映射
            Object outputMappingObj = data.get("outputMapping");
            if (outputMappingObj instanceof Map) {
                Map<String, Object> outputMapping = castMap(outputMappingObj);
                for (Map.Entry<String, Object> entry : outputMapping.entrySet()) {
                    builder.addOutputMapping(entry.getKey(), entry.getValue().toString());
                }
            }

            return builder.build();
        } catch (Exception e) {
            System.err.println("Failed to parse step: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析验证点
     */
    private Validation parseValidation(Map<String, Object> data) {
        try {
            Validation.Builder builder = Validation.builder()
                    .type(Validation.ValidationType.valueOf(getString(data, "type")))
                    .target(getString(data, "target"))
                    .expectedValue(getString(data, "expectedValue"))
                    .actualValuePath(getString(data, "actualValuePath"))
                    .description(getString(data, "description"));

            return builder.build();
        } catch (Exception e) {
            System.err.println("Failed to parse validation: " + e.getMessage());
            return null;
        }
    }

    /**
     * 验证必填字段
     */
    private void validateRequiredFields(Map<String, Object> data) throws IllegalArgumentException {
        List<String> required = Arrays.asList("id", "name", "steps");
        for (String field : required) {
            if (!data.containsKey(field) || data.get(field) == null) {
                throw new IllegalArgumentException("Missing required field: " + field);
            }
        }
    }

    /**
     * 获取字符串值
     */
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取整数值
     */
    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private ScenarioMetadata buildMetadata(Map<String, Object> data) {
        List<String> tags = toStringList(data.get("tags"));
        String serviceName = getString(data, "serviceName");
        String createdBy = getString(data, "createdBy");
        if (tags.isEmpty() && serviceName == null && createdBy == null) {
            return null;
        }

        return ScenarioMetadata.builder()
                .tags(tags)
                .serviceName(serviceName)
                .createdBy(createdBy)
                .build();
    }

    private List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        }
        return result;
    }

    private String joinKeyValuePairs(Map<String, Object> values) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                builder.append(";");
            }
            builder.append(entry.getKey()).append(":").append(entry.getValue());
            first = false;
        }
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private List<Map<String, Object>> castListOfMaps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                result.add(castMap(item));
            }
        }
        return result;
    }
}
