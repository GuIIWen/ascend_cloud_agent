package com.agent.knowledge.v2.service;

import com.agent.knowledge.v2.exception.ScenarioNotFoundException;
import com.agent.knowledge.v2.model.ScenarioMetadata;
import com.agent.knowledge.v2.model.ScenarioStep;
import com.agent.knowledge.v2.model.TestScenario;
import com.agent.knowledge.v2.retriever.TestScenarioRetriever;
import com.agent.service.EmbeddingService;
import com.agent.service.RerankService;
import com.agent.storage.MetadataStore;
import com.agent.storage.VectorStoreAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TestScenarioServiceImpl单元测试
 */
@ExtendWith(MockitoExtension.class)
class TestScenarioServiceImplTest {

    @Mock
    private VectorStoreAdapter vectorStore;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private MetadataStore metadataStore;

    @Mock
    private TestScenarioRetriever retriever;

    private TestScenarioServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TestScenarioServiceImpl(vectorStore, embeddingService, metadataStore, retriever);
    }

    @Test
    void testBuildScenarioIndex() {
        TestScenario scenario = TestScenario.builder()
                .scenarioId("scen_001")
                .name("Test Scenario")
                .description("Test description")
                .addStep(ScenarioStep.builder()
                        .stepOrder(1)
                        .apiId("api1")
                        .description("Step 1")
                        .build())
                .metadata(ScenarioMetadata.builder()
                        .serviceName("TestService")
                        .build())
                .build();

        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        TestScenarioService.IndexStats stats = service.buildScenarioIndex(Arrays.asList(scenario));

        assertNotNull(stats);
        assertEquals(1, stats.getTotalDocuments());
        assertEquals(1, stats.getSuccessCount());
        assertEquals(0, stats.getFailureCount());
        assertTrue(stats.getDurationMs() >= 0);
    }

    @Test
    void testBuildScenarioIndexWithEmptyList() {
        TestScenarioService.IndexStats stats = service.buildScenarioIndex(Collections.emptyList());

        assertNotNull(stats);
        assertEquals(0, stats.getTotalDocuments());
        assertEquals(0, stats.getSuccessCount());
        assertEquals(0, stats.getFailureCount());
    }

    @Test
    void testBuildScenarioIndexWithNull() {
        TestScenarioService.IndexStats stats = service.buildScenarioIndex(null);

        assertNotNull(stats);
        assertEquals(0, stats.getTotalDocuments());
    }

    @Test
    void testSearchScenarios() {
        TestScenario expectedScenario = TestScenario.builder()
                .scenarioId("scen_001")
                .name("Login Test")
                .build();

        when(retriever.retrieve(eq("login"), eq(5))).thenReturn(Arrays.asList(expectedScenario));

        List<TestScenario> results = service.searchScenarios("login", 5);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("scen_001", results.get(0).getScenarioId());
    }

    @Test
    void testFindByApiId() {
        // First add scenarios to cache via index
        TestScenario scenario1 = TestScenario.builder()
                .scenarioId("scen_001")
                .name("Scenario 1")
                .addStep(ScenarioStep.builder()
                        .stepOrder(1)
                        .apiId("api1")
                        .build())
                .build();

        TestScenario scenario2 = TestScenario.builder()
                .scenarioId("scen_002")
                .name("Scenario 2")
                .addStep(ScenarioStep.builder()
                        .stepOrder(1)
                        .apiId("api2")
                        .build())
                .build();

        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        service.buildScenarioIndex(Arrays.asList(scenario1, scenario2));

        List<TestScenario> results = service.findByApiId("api1");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("scen_001", results.get(0).getScenarioId());
    }

    @Test
    void testGetScenarioById() {
        TestScenario scenario = TestScenario.builder()
                .scenarioId("scen_001")
                .name("Test")
                .build();

        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        service.buildScenarioIndex(Arrays.asList(scenario));

        Optional<TestScenario> result = service.getScenarioById("scen_001");

        assertTrue(result.isPresent());
        assertEquals("scen_001", result.get().getScenarioId());
    }

    @Test
    void testGetScenarioByIdNotFound() {
        Optional<TestScenario> result = service.getScenarioById("non_existent");

        assertFalse(result.isPresent());
    }

    @Test
    void testUpdateScenario() {
        TestScenario scenario = TestScenario.builder()
                .scenarioId("scen_001")
                .name("Original Name")
                .metadata(ScenarioMetadata.builder()
                        .updatedAt(1000L)
                        .build())
                .build();

        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        service.buildScenarioIndex(Arrays.asList(scenario));

        TestScenario updated = TestScenario.builder()
                .scenarioId("scen_001")
                .name("Updated Name")
                .metadata(ScenarioMetadata.builder()
                        .updatedAt(1000L)
                        .build())
                .build();

        service.updateScenario(updated);

        Optional<TestScenario> result = service.getScenarioById("scen_001");
        assertTrue(result.isPresent());
        assertEquals("Updated Name", result.get().getName());
    }

    @Test
    void testUpdateScenarioNotFound() {
        TestScenario scenario = TestScenario.builder()
                .scenarioId("non_existent")
                .name("Test")
                .build();

        assertThrows(ScenarioNotFoundException.class, () -> {
            service.updateScenario(scenario);
        });
    }

    @Test
    void testDeleteScenario() {
        TestScenario scenario = TestScenario.builder()
                .scenarioId("scen_001")
                .name("Test")
                .build();

        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        service.buildScenarioIndex(Arrays.asList(scenario));

        service.deleteScenario("scen_001");

        Optional<TestScenario> result = service.getScenarioById("scen_001");
        assertFalse(result.isPresent());
    }

    @Test
    void testDeleteScenarioNotFound() {
        assertThrows(ScenarioNotFoundException.class, () -> {
            service.deleteScenario("non_existent");
        });
    }

    @Test
    void testGetAllScenarios() {
        TestScenario scenario1 = TestScenario.builder()
                .scenarioId("scen_001")
                .name("Test 1")
                .build();

        TestScenario scenario2 = TestScenario.builder()
                .scenarioId("scen_002")
                .name("Test 2")
                .build();

        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        service.buildScenarioIndex(Arrays.asList(scenario1, scenario2));

        Collection<TestScenario> all = service.getAllScenarios();
        assertEquals(2, all.size());
    }

    @Test
    void testGetCacheSize() {
        TestScenario scenario = TestScenario.builder()
                .scenarioId("scen_001")
                .name("Test")
                .build();

        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        assertEquals(0, service.getCacheSize());

        service.buildScenarioIndex(Arrays.asList(scenario));
        assertEquals(1, service.getCacheSize());
    }
}
