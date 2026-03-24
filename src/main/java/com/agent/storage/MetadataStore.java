package com.agent.storage;

import com.agent.model.ApiMetadata;
import com.agent.model.DocumentSourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 元数据存储 - 基于SQLite
 */
public class MetadataStore {
    private static final Logger logger = LoggerFactory.getLogger(MetadataStore.class);

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
                "endpoint TEXT)";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void save(ApiMetadata metadata) throws SQLException {
        String sql = "INSERT OR REPLACE INTO api_metadata VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
                .build();
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
