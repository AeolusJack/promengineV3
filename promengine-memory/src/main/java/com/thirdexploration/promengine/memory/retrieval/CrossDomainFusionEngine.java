package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 跨域融合引擎，支持并行、增强、桥接三种融合模式。
 * 优化：真正的多源RRF融合、主域加权、辅域数量限制。
 */
@Slf4j
@Component
public class CrossDomainFusionEngine {

    private static final int RRF_K = 60;
    private static final double PRIMARY_DOMAIN_BOOST = 1.2;

    public List<MemoryRecord> fuse(List<MemoryRecord> hits, MemoryQuery query) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        FusionMode mode = determineMode(query);
        return switch (mode) {
            case PARALLEL -> parallelFusion(groupBySource(hits));
            case AUGMENT -> augmentFusion(hits, query);
            case BRIDGE -> bridgeFusion(hits);
        };
    }

    private FusionMode determineMode(MemoryQuery query) {
        if (query.getDomains() == null || query.getDomains().size() <= 1) {
            return FusionMode.PARALLEL;
        }
        return FusionMode.AUGMENT;
    }

    /**
     * 按来源（layer）分组，每组内顺序即为原始排序。
     */
    private Map<String, List<MemoryRecord>> groupBySource(List<MemoryRecord> hits) {
        return hits.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(r -> r.getLayer() == null ? "unknown" : r.getLayer(),
                        LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * 并行融合：多源 RRF（Reciprocal Rank Fusion）
     */
    private List<MemoryRecord> parallelFusion(Map<String, List<MemoryRecord>> sources) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, MemoryRecord> docMap = new HashMap<>();

        for (List<MemoryRecord> list : sources.values()) {
            for (int rank = 0; rank < list.size(); rank++) {
                MemoryRecord hit = list.get(rank);
                if (hit == null) continue;
                String id = hit.getId();
                docMap.putIfAbsent(id, hit);
                double rrfScore = 1.0 / (RRF_K + rank + 1);
                scores.merge(id, rrfScore, Double::sum);
            }
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> docMap.get(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 增强融合：主域优先，辅域按比例补充
     */
    private List<MemoryRecord> augmentFusion(List<MemoryRecord> hits, MemoryQuery query) {
        String primaryDomain = query.getEffectiveDomain();
        List<MemoryRecord> primary = hits.stream()
                .filter(h -> primaryDomain.equals(h.getDomain()))
                .collect(Collectors.toList());
        List<MemoryRecord> secondary = hits.stream()
                .filter(h -> !primaryDomain.equals(h.getDomain()))
                .collect(Collectors.toList());

        // 限制辅域数量不超过主域的两倍，避免稀释
        int limitSecondary = Math.min(secondary.size(), primary.size() * 2);
        List<MemoryRecord> result = new ArrayList<>(primary);
        result.addAll(secondary.subList(0, limitSecondary));
        return result.stream().distinct().collect(Collectors.toList());
    }

    private List<MemoryRecord> bridgeFusion(List<MemoryRecord> hits) {
        return parallelFusion(groupBySource(hits));
    }

    enum FusionMode { PARALLEL, AUGMENT, BRIDGE }
}