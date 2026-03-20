package com.agent.knowledge.v2.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScenarioStep单元测试
 */
class ScenarioStepTest {

    @Test
    void testBuilderPattern() {
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("userId", "123");
        inputParams.put("token", "abc");

        Map<String, String> outputMapping = new HashMap<>();
        outputMapping.put("userId", "$.response.userId");
        outputMapping.put("token", "$.response.token");

        ScenarioStep step = ScenarioStep.builder()
                .stepOrder(1)
                .stepId("step_001")
                .apiId("com.example.UserService.login")
                .description("User login")
                .inputParams(inputParams)
                .paramExtractFromPrev("userId:$.steps[1].output.userId")
                .outputMapping(outputMapping)
                .build();

        assertEquals(1, step.getStepOrder());
        assertEquals("step_001", step.getStepId());
        assertEquals("com.example.UserService.login", step.getApiId());
        assertEquals("User login", step.getDescription());
        assertEquals("123", step.getInputParams().get("userId"));
        assertEquals("userId:$.steps[1].output.userId", step.getParamExtractFromPrev());
        assertEquals("$.response.userId", step.getOutputMapping().get("userId"));
    }

    @Test
    void testDefaultStepId() {
        ScenarioStep step = new ScenarioStep();
        assertNotNull(step.getStepId());
        assertTrue(step.getStepId().startsWith("step_"));
    }

    @Test
    void testInputParamsAreCopied() {
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("key", "value");

        ScenarioStep step = ScenarioStep.builder()
                .inputParams(inputParams)
                .build();

        // Verify it's a copy
        assertNotSame(inputParams, step.getInputParams());
        assertEquals(inputParams, step.getInputParams());
    }

    @Test
    void testEqualsAndHashCode() {
        ScenarioStep step1 = ScenarioStep.builder()
                .stepId("step_123")
                .build();

        ScenarioStep step2 = ScenarioStep.builder()
                .stepId("step_123")
                .build();

        ScenarioStep step3 = ScenarioStep.builder()
                .stepId("step_456")
                .build();

        assertEquals(step1, step2);
        assertEquals(step1.hashCode(), step2.hashCode());
        assertNotEquals(step1, step3);
    }
}
