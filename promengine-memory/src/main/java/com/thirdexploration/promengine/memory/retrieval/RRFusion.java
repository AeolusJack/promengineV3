package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.core.domain.SearchResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reciprocal Rank Fusion 融合算法。
 */
public final class RRFusion {

    private static final int K = 60;

    private RRFusion() {}

    public static List<SearchResult.MemoryHit> fuse(List<SearchResult.MemoryHit> hits, int topK) {
        // 按来源分组，计算每个文档的 RRF 分数
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, SearchResult.MemoryHit> docMap = new HashMap<>();

        // 假设 hits 已按各自来源排序（调用前应保证）
        // 简单起见，我们将 hits 按来源分组并赋予排名
        Map<String, List<SearchResult.MemoryHit>> bySource = hits.stream()
                .collect(Collectors.groupingBy(h -> h.getScore() > 0.9 ? "hot" : "warm")); // 示例分组

        for (var entry : bySource.entrySet()) {
            List<SearchResult.MemoryHit> sourceHits = entry.getValue();
            for (int rank = 0; rank < sourceHits.size(); rank++) {
                SearchResult.MemoryHit hit = sourceHits.get(rank);
                docMap.putIfAbsent(hit.getMemoryId(), hit);
                double rrf = 1.0 / (K + rank + 1);
                rrfScores.merge(hit.getMemoryId(), rrf, Double::sum);
            }
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    SearchResult.MemoryHit hit = docMap.get(e.getKey());
                    return SearchResult.MemoryHit.builder()
                            .memoryId(hit.getMemoryId())
                            .content(hit.getContent())
                            .timestamp(hit.getTimestamp())
                            .score(e.getValue().floatValue())
                            .build();
                })
                .toList();
    }
}