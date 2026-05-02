package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * aeon
 * 跨域融合引擎，支持并行、增强、桥接三种融合模式。
 */
@Component
public class CrossDomainFusionEngine {

    public List<MemoryRecord> fuse(List<MemoryRecord> hits, MemoryQuery query) {
        FusionMode mode = determineMode(query);
        return switch (mode) {
            case PARALLEL -> parallelFusion(hits);
            case AUGMENT -> augmentFusion(hits, query);
            case BRIDGE -> bridgeFusion(hits);
        };
    }

    private FusionMode determineMode(MemoryQuery query) {
        List<String> domains = query.getDomains();
        if (domains.size() == 1) {
            return FusionMode.PARALLEL;
        }
        return FusionMode.AUGMENT;
    }

    /**
     * 并行融合：RRF 加权融合。
     */
    private List<MemoryRecord> parallelFusion(List<MemoryRecord> hits) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, MemoryRecord> docMap = new HashMap<>();
        int k = 60;

        for (int i = 0; i < hits.size(); i++) {
            MemoryRecord hit = hits.get(i);
            docMap.putIfAbsent(hit.getId(), hit);
            scores.merge(hit.getId(), 1.0 / (k + i + 1), Double::sum);
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> docMap.get(e.getKey()))
                .toList();
    }

    /**
     * 增强融合：主域结果优先，辅域补充。
     */
    private List<MemoryRecord> augmentFusion(List<MemoryRecord> hits, MemoryQuery query) {
        String primaryDomain = query.getEffectiveDomain();
        List<MemoryRecord> primary = hits.stream()
                .filter(h -> primaryDomain.equals(h.getDomain()))
                .toList();
        List<MemoryRecord> secondary = hits.stream()
                .filter(h -> !primaryDomain.equals(h.getDomain()))
                .toList();

        List<MemoryRecord> result = new ArrayList<>(primary);
        result.addAll(secondary);
        return result.stream().distinct().toList();
    }

    /**
     * 桥接融合：通过图谱关系桥接（简化实现）。
     */
    private List<MemoryRecord> bridgeFusion(List<MemoryRecord> hits) {
        return parallelFusion(hits);
    }

    enum FusionMode { PARALLEL, AUGMENT, BRIDGE }
}