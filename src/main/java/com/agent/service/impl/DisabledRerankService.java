package com.agent.service.impl;

import com.agent.service.RerankService;

import java.util.ArrayList;
import java.util.List;

/**
 * 占位Rerank服务，保留原始召回顺序，避免未启用时影响启动和基础检索。
 */
public class DisabledRerankService implements RerankService {

    @Override
    public List<RerankResult> rerank(String query, List<String> candidates) {
        List<RerankResult> results = new ArrayList<>();
        if (candidates == null) {
            return results;
        }
        for (int i = 0; i < candidates.size(); i++) {
            results.add(new RerankResult(i, 1.0d / (i + 1)));
        }
        return results;
    }
}
