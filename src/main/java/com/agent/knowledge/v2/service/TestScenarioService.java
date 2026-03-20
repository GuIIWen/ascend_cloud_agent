package com.agent.knowledge.v2.service;

import com.agent.knowledge.v2.model.TestScenario;

import java.util.List;
import java.util.Optional;

/**
 * 测试场景服务接口
 */
public interface TestScenarioService {

    /**
     * 构建场景索引
     *
     * @param scenarios 要索引的场景列表
     * @return 索引统计信息
     */
    IndexStats buildScenarioIndex(List<TestScenario> scenarios);

    /**
     * 检索场景
     *
     * @param query 查询文本
     * @param topK 返回结果数量
     * @return 匹配的TestScenario列表
     */
    List<TestScenario> searchScenarios(String query, int topK);

    /**
     * 根据API ID查找相关场景
     *
     * @param apiId API标识
     * @return 包含该API的场景列表
     */
    List<TestScenario> findByApiId(String apiId);

    /**
     * 根据场景ID获取场景
     *
     * @param scenarioId 场景标识
     * @return 场景详情
     */
    Optional<TestScenario> getScenarioById(String scenarioId);

    /**
     * 更新场景
     *
     * @param scenario 要更新的场景
     */
    void updateScenario(TestScenario scenario);

    /**
     * 删除场景
     *
     * @param scenarioId 要删除的场景ID
     */
    void deleteScenario(String scenarioId);

    /**
     * 索引统计信息
     */
    class IndexStats {
        private final int totalDocuments;
        private final int successCount;
        private final int failureCount;
        private final long durationMs;

        public IndexStats(int totalDocuments, int successCount, int failureCount, long durationMs) {
            this.totalDocuments = totalDocuments;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.durationMs = durationMs;
        }

        public int getTotalDocuments() {
            return totalDocuments;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public long getDurationMs() {
            return durationMs;
        }

        @Override
        public String toString() {
            return "IndexStats{" +
                    "totalDocuments=" + totalDocuments +
                    ", successCount=" + successCount +
                    ", failureCount=" + failureCount +
                    ", durationMs=" + durationMs +
                    '}';
        }
    }
}
