package com.agent.service;

import java.util.List;

/**
 * Rerank服务接口
 */
public interface RerankService {

    /**
     * 对候选结果重排序
     * @param query 查询文本
     * @param candidates 候选文本列表
     * @return 排序后的索引和分数
     */
    List<RerankResult> rerank(String query, List<String> candidates);

    /**
     * Rerank结果
     */
    class RerankResult {
        private int index;
        private double score;

        public RerankResult(int index, double score) {
            this.index = index;
            this.score = score;
        }

        public int getIndex() { return index; }
        public double getScore() { return score; }
    }
}
