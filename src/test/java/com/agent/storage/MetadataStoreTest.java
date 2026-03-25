package com.agent.storage;

import com.agent.model.ApiMetadata;
import com.agent.model.DocumentSourceType;
import com.agent.model.Parameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataStoreTest {

    @Test
    void saveAndFindByApiIdRoundTripsStructuredFields(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("metadata.db");
        MetadataStore store = new MetadataStore(dbPath.toString());

        ApiMetadata metadata = ApiMetadata.builder()
                .apiId("api-1")
                .className("DETACH_DEV_SERVER_VOLUME")
                .methodName("DetachDevServerVolume")
                .description("detach volume")
                .sourceType(DocumentSourceType.WEB_PAGE)
                .sourceLocation("https://support.huaweicloud.com/api-modelarts/DetachDevServerVolume.html")
                .httpMethod("DELETE")
                .endpoint("/v1/{project_id}/dev-servers/{id}/detachvolume/{volume_id}")
                .requestBody("{\"volume_id\":\"123\"}")
                .responseBody("{\"status\":\"ok\"}")
                .parameters(List.of(
                        new Parameter("id", "string", "Lite Server id", true),
                        new Parameter("volume_id", "string", "volume id", true)))
                .build();

        store.save(metadata);
        ApiMetadata restored = store.findByApiId("api-1");

        assertNotNull(restored);
        assertEquals("{\"volume_id\":\"123\"}", restored.getRequestBody());
        assertEquals("{\"status\":\"ok\"}", restored.getResponseBody());
        assertEquals(2, restored.getParameters().size());
        assertEquals("id", restored.getParameters().get(0).getName());
        assertEquals("volume_id", restored.getParameters().get(1).getName());

        store.close();
    }

    @Test
    void initMigratesLegacySchemaAndAllowsStructuredFields(@TempDir Path tempDir) throws Exception {
        Path dbPath = tempDir.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE api_metadata (
                        api_id TEXT PRIMARY KEY,
                        class_name TEXT,
                        method_name TEXT,
                        signature TEXT,
                        description TEXT,
                        source_type TEXT,
                        source_location TEXT,
                        return_type TEXT,
                        http_method TEXT,
                        endpoint TEXT
                    )
                    """);
            statement.execute("""
                    INSERT INTO api_metadata (
                        api_id, class_name, method_name, signature, description, source_type, source_location,
                        return_type, http_method, endpoint
                    ) VALUES (
                        'legacy-api', 'LEGACY_API', 'LegacyApi', null, 'legacy description', 'WEB_PAGE',
                        'memory://legacy', null, 'GET', '/legacy'
                    )
                    """);
        }

        MetadataStore store = new MetadataStore(dbPath.toString());

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(api_metadata)")) {
            List<String> columns = new java.util.ArrayList<>();
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
            assertTrue(columns.contains("request_body"));
            assertTrue(columns.contains("response_body"));
            assertTrue(columns.contains("parameters"));
        }

        ApiMetadata legacy = store.findByApiId("legacy-api");
        assertNotNull(legacy);
        assertEquals("/legacy", legacy.getEndpoint());
        assertTrue(legacy.getParameters().isEmpty());

        ApiMetadata upgraded = ApiMetadata.builder()
                .apiId("upgraded-api")
                .className("UPGRADED_API")
                .methodName("UpgradedApi")
                .description("upgraded description")
                .sourceType(DocumentSourceType.WEB_PAGE)
                .sourceLocation("memory://upgraded")
                .httpMethod("POST")
                .endpoint("/upgraded")
                .requestBody("{\"name\":\"demo\"}")
                .responseBody("{\"ok\":true}")
                .parameters(List.of(new Parameter("name", "string", "demo name", true)))
                .build();

        store.save(upgraded);
        ApiMetadata restored = store.findByApiId("upgraded-api");

        assertNotNull(restored);
        assertEquals("{\"name\":\"demo\"}", restored.getRequestBody());
        assertEquals("{\"ok\":true}", restored.getResponseBody());
        assertEquals(1, restored.getParameters().size());
        assertEquals("name", restored.getParameters().getFirst().getName());

        store.close();
    }
}
