package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.memory.model.MemoryRecord;

import java.util.*;

/**
 * Reciprocal Rank Fusion 融合算法工具类。
 */
public final class RRFusion {

    private static final int K = 60;

    private RRFusion() {}

    public static List<MemoryRecord> fuseWithWeights(List<RankedRecord> rankedRecords, int topK) {
        if (rankedRecords == null || rankedRecords.isEmpty()) return List.of();
        Map<String, Double> scores = new HashMap<>();
        Map<String, MemoryRecord> docMap = new HashMap<>();

        for (RankedRecord rr : rankedRecords) {
            MemoryRecord record = rr.record();
            if (record == null) continue;
            docMap.putIfAbsent(record.getId(), record);
            double weightedScore = rr.weight() / (K + rr.rank() + 1);
            scores.merge(record.getId(), weightedScore, Double::sum);
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> docMap.get(e.getKey()))
                .filter(Objects::nonNull)
                .toList();
    }

    public record RankedRecord(MemoryRecord record, int rank, double weight) {}
}