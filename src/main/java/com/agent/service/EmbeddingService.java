package com.agent.service;

import java.util.List;

/**
 * Embedding服务接口
 */
public interface EmbeddingService {

    /**
     * 将文本转换为向量
     */
    float[] embed(String text);

    /**
     * 批量转换
     */
    List<float[]> embedBatch(List<String> texts);
}
