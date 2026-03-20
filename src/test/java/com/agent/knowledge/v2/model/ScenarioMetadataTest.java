package com.agent.knowledge.v2.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScenarioMetadata单元测试
 */
class ScenarioMetadataTest {

    @Test
    void testBuilderPattern() {
        List<String> tags = Arrays.asList("tag1", "tag2", "tag3");

        ScenarioMetadata metadata = ScenarioMetadata.builder()
                .serviceName("ModelArts")
                .tags(tags)
                .createdBy("testUser")
                .createdAt(1000000L)
                .updatedAt(2000000L)
                .version("2.0.0")
                .build();

        assertEquals("ModelArts", metadata.getServiceName());
        assertEquals(3, metadata.getTags().size());
        assertEquals("testUser", metadata.getCreatedBy());
        assertEquals(1000000L, metadata.getCreatedAt());
        assertEquals(2000000L, metadata.getUpdatedAt());
        assertEquals("2.0.0", metadata.getVersion());
    }

    @Test
    void testDefaultValues() {
        ScenarioMetadata metadata = new ScenarioMetadata();

        assertNotNull(metadata.getTags());
        assertTrue(metadata.getTags().isEmpty());
        assertEquals("1.0.0", metadata.getVersion());
        assertTrue(metadata.getCreatedAt() > 0);
        assertTrue(metadata.getUpdatedAt() > 0);
    }

    @Test
    void testAddTag() {
        ScenarioMetadata metadata = ScenarioMetadata.builder()
                .addTag("tag1")
                .addTag("tag2")
                .build();

        assertEquals(2, metadata.getTags().size());
        assertTrue(metadata.getTags().contains("tag1"));
        assertTrue(metadata.getTags().contains("tag2"));
    }

    @Test
    void testTouch() {
        ScenarioMetadata metadata = ScenarioMetadata.builder()
                .createdAt(1000000L)
                .updatedAt(1000000L)
                .build();

        long before = metadata.getUpdatedAt();

        // Small delay to ensure time difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // Ignore
        }

        metadata.touch();

        assertTrue(metadata.getUpdatedAt() > before);
    }

    @Test
    void testTagsAreCopied() {
        List<String> tags = Arrays.asList("tag1", "tag2");
        ScenarioMetadata metadata = ScenarioMetadata.builder()
                .tags(tags)
                .build();

        assertNotSame(tags, metadata.getTags());
        assertEquals(tags, metadata.getTags());
    }

    @Test
    void testEqualsAndHashCode() {
        ScenarioMetadata meta1 = ScenarioMetadata.builder()
                .serviceName("Service")
                .createdBy("user")
                .createdAt(1000L)
                .updatedAt(2000L)
                .version("1.0")
                .build();

        ScenarioMetadata meta2 = ScenarioMetadata.builder()
                .serviceName("Service")
                .createdBy("user")
                .createdAt(1000L)
                .updatedAt(2000L)
                .version("1.0")
                .build();

        // Same content should be equal
        assertEquals(meta1, meta2);
        assertEquals(meta1.hashCode(), meta2.hashCode());
    }
}
