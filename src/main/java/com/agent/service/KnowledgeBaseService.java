package com.agent.service;

import com.agent.model.ApiMetadata;
import com.agent.model.DocumentSource;

import java.util.List;

/**
 * 知识库服务接口
 */
public interface KnowledgeBaseService {

    /**
     * 索引Java项目
     */
    IndexStats indexJavaProject(String projectPath);

    /**
     * 索引外部文档
     */
    IndexStats indexExternalDocs(List<DocumentSource> sources);

    /**
     * 语义检索
     */
    List<ApiMetadata> search(String query, int topK);

    /**
     * 增量更新
     */
    void updateIndex(List<String> changedFiles);

    /**
     * 索引统计
     */
    class IndexStats {
        private int totalDocuments;
        private int successCount;
        private int failureCount;
        private long durationMs;

        public IndexStats(int totalDocuments, int successCount, int failureCount, long durationMs) {
            this.totalDocuments = totalDocuments;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.durationMs = durationMs;
        }

        public int getTotalDocuments() { return totalDocuments; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public long getDurationMs() { return durationMs; }
    }
}
