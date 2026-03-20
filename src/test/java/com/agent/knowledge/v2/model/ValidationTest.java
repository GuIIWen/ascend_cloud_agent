package com.agent.knowledge.v2.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validation单元测试
 */
class ValidationTest {

    @Test
    void testBuilderPattern() {
        Validation validation = Validation.builder()
                .validationId("val_001")
                .type(Validation.ValidationType.valueOf("ASSERT_EQUAL"))
                .target("$.status")
                .expectedValue("200")
                .actualValuePath("$.response.status")
                .description("Status code check")
                .build();

        assertEquals("val_001", validation.getValidationId());
        assertEquals(Validation.ValidationType.valueOf("ASSERT_EQUAL"), validation.getType());
        assertEquals("$.status", validation.getTarget());
        assertEquals("200", validation.getExpectedValue());
        assertEquals("$.response.status", validation.getActualValuePath());
        assertEquals("Status code check", validation.getDescription());
    }

    @Test
    void testDefaultValidationId() {
        Validation validation = new Validation();
        assertNotNull(validation.getValidationId());
        assertTrue(validation.getValidationId().startsWith("val_"));
    }

    @Test
    void testValidationTypeValues() {
        Validation.ValidationType[] types = Validation.ValidationType.values();
        assertEquals(5, types.length);
        assertEquals(Validation.ValidationType.valueOf("ASSERT_EQUAL"), types[0]);
        assertEquals(Validation.ValidationType.valueOf("ASSERT_NOT_NULL"), types[1]);
        assertEquals(Validation.ValidationType.valueOf("ASSERT_CONTAINS"), types[2]);
        assertEquals(Validation.ValidationType.valueOf("ASSERT_STATUS"), types[3]);
        assertEquals(Validation.ValidationType.valueOf("ASSERT_JSON_PATH"), types[4]);
    }

    @Test
    void testEqualsAndHashCode() {
        Validation val1 = Validation.builder()
                .validationId("val_123")
                .build();

        Validation val2 = Validation.builder()
                .validationId("val_123")
                .build();

        Validation val3 = Validation.builder()
                .validationId("val_456")
                .build();

        assertEquals(val1, val2);
        assertEquals(val1.hashCode(), val2.hashCode());
        assertNotEquals(val1, val3);
    }
}
