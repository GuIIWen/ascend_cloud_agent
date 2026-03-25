package com.agent.storage;

import com.agent.model.ApiMetadata;
import com.agent.model.DocumentSourceType;
import com.agent.model.Parameter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 元数据存储 - 基于SQLite
 */
public class MetadataStore {
    private static final Logger logger = LoggerFactory.getLogger(MetadataStore.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, String> REQUIRED_COLUMNS = new LinkedHashMap<>();

    static {
        REQUIRED_COLUMNS.put("request_body", "TEXT");
        REQUIRED_COLUMNS.put("response_body", "TEXT");
        REQUIRED_COLUMNS.put("parameters", "TEXT");
    }

    private final String dbPath;
    private Connection connection;

    public MetadataStore(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }

    private void initDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            createTables();
            migrateSchemaIfNeeded();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS api_metadata (" +
                "api_id TEXT PRIMARY KEY, " +
                "class_name TEXT, " +
                "method_name TEXT, " +
                "signature TEXT, " +
                "description TEXT, " +
                "source_type TEXT, " +
                "source_location TEXT, " +
                "return_type TEXT, " +
                "http_method TEXT, " +
                "endpoint TEXT, " +
                "request_body TEXT, " +
                "response_body TEXT, " +
                "parameters TEXT)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void migrateSchemaIfNeeded() throws SQLException {
        List<String> existingColumns = listColumns();
        try (Statement stmt = connection.createStatement()) {
            for (Map.Entry<String, String> entry : REQUIRED_COLUMNS.entrySet()) {
                if (!existingColumns.contains(entry.getKey())) {
                    stmt.execute("ALTER TABLE api_metadata ADD COLUMN " + entry.getKey() + " " + entry.getValue());
                }
            }
        }
    }

    private List<String> listColumns() throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(api_metadata)")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    public void save(ApiMetadata metadata) throws SQLException {
        String sql = "INSERT OR REPLACE INTO api_metadata " +
                "(api_id, class_name, method_name, signature, description, source_type, source_location, " +
                "return_type, http_method, endpoint, request_body, response_body, parameters) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, metadata.getApiId());
            pstmt.setString(2, metadata.getClassName());
            pstmt.setString(3, metadata.getMethodName());
            pstmt.setString(4, metadata.getSignature());
            pstmt.setString(5, metadata.getDescription());
            pstmt.setString(6, metadata.getSourceType() != null ? metadata.getSourceType().name() : null);
            pstmt.setString(7, metadata.getSourceLocation());
            pstmt.setString(8, metadata.getReturnType());
            pstmt.setString(9, metadata.getHttpMethod());
            pstmt.setString(10, metadata.getEndpoint());
            pstmt.setString(11, metadata.getRequestBody());
            pstmt.setString(12, metadata.getResponseBody());
            pstmt.setString(13, serializeParameters(metadata.getParameters()));
            pstmt.executeUpdate();
        }
    }

    public ApiMetadata findByApiId(String apiId) throws SQLException {
        String sql = "SELECT * FROM api_metadata WHERE api_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, apiId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSet(rs);
            }
        }

        return null;
    }

    public ApiMetadata findById(String id) throws SQLException {
        return findByApiId(id);
    }

    public List<ApiMetadata> findBySource(DocumentSourceType sourceType) throws SQLException {
        String sql = "SELECT * FROM api_metadata WHERE source_type = ?";
        List<ApiMetadata> results = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sourceType.name());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                results.add(mapResultSet(rs));
            }
        }

        return results;
    }

    private ApiMetadata mapResultSet(ResultSet rs) throws SQLException {
        String sourceTypeValue = rs.getString("source_type");
        return ApiMetadata.builder()
                .apiId(rs.getString("api_id"))
                .className(rs.getString("class_name"))
                .methodName(rs.getString("method_name"))
                .signature(rs.getString("signature"))
                .description(rs.getString("description"))
                .sourceType(parseSourceType(sourceTypeValue))
                .sourceLocation(rs.getString("source_location"))
                .returnType(rs.getString("return_type"))
                .httpMethod(rs.getString("http_method"))
                .endpoint(rs.getString("endpoint"))
                .requestBody(rs.getString("request_body"))
                .responseBody(rs.getString("response_body"))
                .parameters(deserializeParameters(rs.getString("parameters")))
                .build();
    }

    private String serializeParameters(List<Parameter> parameters) throws SQLException {
        List<Parameter> safeParameters = parameters == null ? List.of() : parameters;
        try {
            return OBJECT_MAPPER.writeValueAsString(safeParameters);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize parameters", e);
        }
    }

    private List<Parameter> deserializeParameters(String raw) throws SQLException {
        if (raw == null || raw.trim().isEmpty()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(raw, new TypeReference<List<Parameter>>() {});
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to deserialize parameters", e);
        }
    }

    private DocumentSourceType parseSourceType(String sourceTypeValue) {
        if (sourceTypeValue == null || sourceTypeValue.trim().isEmpty()) {
            return null;
        }
        try {
            return DocumentSourceType.valueOf(sourceTypeValue);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown source_type '{}' found in metadata store, degrading to null", sourceTypeValue);
            return null;
        }
    }

    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
}
