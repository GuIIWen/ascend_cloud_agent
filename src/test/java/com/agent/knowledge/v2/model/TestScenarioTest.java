package com.agent.knowledge.v2.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestScenario单元测试
 */
class TestScenarioTest {

    @Test
    void testBuilderPattern() {
        ScenarioMetadata metadata = ScenarioMetadata.builder()
                .serviceName("TestService")
                .addTag("tag1")
                .createdBy("test")
                .build();

        ScenarioStep step = ScenarioStep.builder()
                .stepOrder(1)
                .apiId("api1")
                .description("test step")
                .build();

        TestScenario scenario = TestScenario.builder()
                .scenarioId("scen_123")
                .name("Test Scenario")
                .description("Test Description")
                .addStep(step)
                .metadata(metadata)
                .build();

        assertEquals("scen_123", scenario.getScenarioId());
        assertEquals("Test Scenario", scenario.getName());
        assertEquals("Test Description", scenario.getDescription());
        assertEquals(1, scenario.getSteps().size());
        assertEquals(metadata, scenario.getMetadata());
    }

    @Test
    void testToText() {
        ScenarioStep step1 = ScenarioStep.builder()
                .stepOrder(1)
                .apiId("api1")
                .description("First step")
                .build();

        ScenarioStep step2 = ScenarioStep.builder()
                .stepOrder(2)
                .apiId("api2")
                .description("Second step")
                .build();

        Validation validation = Validation.builder()
                .type(Validation.ValidationType.valueOf("ASSERT_STATUS"))
                .target("$.status")
                .expectedValue("200")
                .description("Check status")
                .build();

        TestScenario scenario = TestScenario.builder()
                .scenarioId("scen_123")
                .name("Multi-step Test")
                .description("A multi-step test scenario")
                .addStep(step1)
                .addStep(step2)
                .addValidation(validation)
                .build();

        String text = scenario.toText();

        assertTrue(text.contains("Scenario: Multi-step Test"));
        assertTrue(text.contains("Description: A multi-step test scenario"));
        assertTrue(text.contains("Steps:"));
        assertTrue(text.contains("1. First step"));
        assertTrue(text.contains("2. Second step"));
        assertTrue(text.contains("Validations:"));
        assertTrue(text.contains("Check status"));
    }

    @Test
    void testEqualsAndHashCode() {
        TestScenario scenario1 = TestScenario.builder()
                .scenarioId("scen_123")
                .name("Test")
                .build();

        TestScenario scenario2 = TestScenario.builder()
                .scenarioId("scen_123")
                .name("Different Name")
                .build();

        TestScenario scenario3 = TestScenario.builder()
                .scenarioId("scen_456")
                .name("Test")
                .build();

        assertEquals(scenario1, scenario2);
        assertEquals(scenario1.hashCode(), scenario2.hashCode());
        assertNotEquals(scenario1, scenario3);
    }

    @Test
    void testStepsAndValidationsAreCopied() {
        ScenarioStep step = ScenarioStep.builder()
                .stepOrder(1)
                .build();

        Validation validation = Validation.builder()
                .type(Validation.ValidationType.valueOf("ASSERT_NOT_NULL"))
                .build();

        TestScenario scenario = TestScenario.builder()
                .steps(Arrays.asList(step))
                .validations(Arrays.asList(validation))
                .build();

        // Verify they are copies, not references
        assertNotSame(step, scenario.getSteps().get(0));
        assertNotSame(validation, scenario.getValidations().get(0));
    }
}
