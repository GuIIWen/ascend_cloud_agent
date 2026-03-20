package com.agent.knowledge.v2.builder;

import com.agent.knowledge.v2.model.ScenarioStep;
import com.agent.knowledge.v2.model.TestScenario;
import com.agent.knowledge.v2.model.Validation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YamlScenarioLoader单元测试
 */
class YamlScenarioLoaderTest {

    @Test
    void testParseSimpleScenario() {
        String yaml = "scenarios:\n" +
                "  - id: scen_001\n" +
                "    name: Simple Login Test\n" +
                "    description: Test user login flow\n" +
                "    serviceName: AuthService\n" +
                "    steps:\n" +
                "      - order: 1\n" +
                "        apiId: com.example.auth.login\n" +
                "        description: User login\n" +
                "    validations:\n" +
                "      - type: ASSERT_STATUS\n" +
                "        target: $.status\n" +
                "        expectedValue: \"200\"\n" +
                "        description: Check status\n";

        YamlScenarioLoader loader = new YamlScenarioLoader();
        List<TestScenario> scenarios = assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> loader.parseYaml(yaml));

        assertNotNull(scenarios);
        assertEquals(1, scenarios.size());

        TestScenario scenario = scenarios.get(0);
        assertEquals("scen_001", scenario.getScenarioId());
        assertEquals("Simple Login Test", scenario.getName());
        assertEquals("Test user login flow", scenario.getDescription());
        assertEquals(1, scenario.getSteps().size());

        ScenarioStep step = scenario.getSteps().get(0);
        assertEquals(1, step.getStepOrder());
        assertEquals("com.example.auth.login", step.getApiId());
        assertEquals("User login", step.getDescription());

        assertEquals(1, scenario.getValidations().size());
        Validation validation = scenario.getValidations().get(0);
        assertEquals(Validation.ValidationType.valueOf("ASSERT_STATUS"), validation.getType());
        assertEquals("$.status", validation.getTarget());
        assertEquals("200", validation.getExpectedValue());
    }

    @Test
    void testParseMultiStepScenario() {
        String yaml = "scenarios:\n" +
                "  - id: scen_002\n" +
                "    name: Login and Get Orders\n" +
                "    description: User login then get orders\n" +
                "    tags:\n" +
                "      - login\n" +
                "      - orders\n" +
                "    steps:\n" +
                "      - order: 1\n" +
                "        apiId: com.example.auth.login\n" +
                "        description: User login\n" +
                "        outputMapping:\n" +
                "          token: \"$.response.token\"\n" +
                "          userId: \"$.response.userId\"\n" +
                "      - order: 2\n" +
                "        apiId: com.example.orders.list\n" +
                "        description: Get user orders\n" +
                "        paramExtractFromPrev:\n" +
                "          userId: \"$.steps[1].output.userId\"\n" +
                "          token: \"$.steps[1].output.token\"\n" +
                "        inputParams:\n" +
                "          limit: 10\n";

        YamlScenarioLoader loader = new YamlScenarioLoader();
        List<TestScenario> scenarios = loader.parseYaml(yaml);

        assertNotNull(scenarios);
        assertEquals(1, scenarios.size());

        TestScenario scenario = scenarios.get(0);
        assertEquals("scen_002", scenario.getScenarioId());
        assertEquals(2, scenario.getSteps().size());

        ScenarioStep step1 = scenario.getSteps().get(0);
        assertEquals(1, step1.getStepOrder());
        assertEquals("com.example.auth.login", step1.getApiId());
        assertEquals("$.response.token", step1.getOutputMapping().get("token"));

        ScenarioStep step2 = scenario.getSteps().get(1);
        assertEquals(2, step2.getStepOrder());
        assertEquals("com.example.orders.list", step2.getApiId());
        assertNotNull(step2.getParamExtractFromPrev());
        assertEquals("10", step2.getInputParams().get("limit").toString());
    }

    @Test
    void testParseMultipleScenarios() {
        String yaml = "scenarios:\n" +
                "  - id: scen_001\n" +
                "    name: Scenario One\n" +
                "    description: First scenario\n" +
                "    steps:\n" +
                "      - order: 1\n" +
                "        apiId: api1\n" +
                "        description: Step 1\n" +
                "  - id: scen_002\n" +
                "    name: Scenario Two\n" +
                "    description: Second scenario\n" +
                "    steps:\n" +
                "      - order: 1\n" +
                "        apiId: api2\n" +
                "        description: Step 2\n";

        YamlScenarioLoader loader = new YamlScenarioLoader();
        List<TestScenario> scenarios = loader.parseYaml(yaml);

        assertNotNull(scenarios);
        assertEquals(2, scenarios.size());

        assertEquals("scen_001", scenarios.get(0).getScenarioId());
        assertEquals("scen_002", scenarios.get(1).getScenarioId());
    }

    @Test
    void testParseYamlWithComments() {
        String yaml = "# This is a comment\n" +
                "scenarios:\n" +
                "  - id: scen_001  # inline comment\n" +
                "    name: Test\n" +
                "    description: Test scenario\n" +
                "    steps:\n" +
                "      - order: 1\n" +
                "        apiId: api1\n" +
                "        description: Step\n";

        YamlScenarioLoader loader = new YamlScenarioLoader();
        List<TestScenario> scenarios = loader.parseYaml(yaml);

        assertNotNull(scenarios);
        assertEquals(1, scenarios.size());
    }

    @Test
    void testParseInlineList() {
        String yaml = "scenarios:\n" +
                "  - id: scen_001\n" +
                "    name: Test\n" +
                "    description: Test\n" +
                "    tags: [\"tag1\", \"tag2\", \"tag3\"]\n" +
                "    steps:\n" +
                "      - order: 1\n" +
                "        apiId: api1\n" +
                "        description: Step\n";

        YamlScenarioLoader loader = new YamlScenarioLoader();
        List<TestScenario> scenarios = loader.parseYaml(yaml);

        assertNotNull(scenarios);
        assertEquals(1, scenarios.size());

        TestScenario scenario = scenarios.get(0);
        assertNotNull(scenario.getMetadata());
        assertEquals(3, scenario.getMetadata().getTags().size());
    }

    @Test
    void testParseEmptyYaml() {
        YamlScenarioLoader loader = new YamlScenarioLoader();
        List<TestScenario> scenarios = loader.parseYaml("");

        assertNotNull(scenarios);
        assertTrue(scenarios.isEmpty());
    }
}
