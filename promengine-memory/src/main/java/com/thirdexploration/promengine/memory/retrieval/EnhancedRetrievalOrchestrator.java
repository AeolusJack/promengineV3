package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.memory.config.AeonMemoryProperties;
import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.storage.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * aeon
 * 增强检索编排器，负责协调多层、多域检索，并融合结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedRetrievalOrchestrator {

    private final WorkingMemoryManager workingMemory;
    private final EpisodicMemoryService episodicMemory;
    private final SemanticMemoryService semanticMemory;
    private final ProceduralMemoryService proceduralMemory;
    private final CollectiveMemoryService collectiveMemory;

    private final DualCoreRouter dualCoreRouter;
    private final CrossDomainFusionEngine fusionEngine;
    private final AeonMemoryProperties properties;
    private final MemoryMetadataRegistry registry;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 主检索入口。
     */
    public List<MemoryEntry> retrieve(MemoryQuery query) {
        long start = System.currentTimeMillis();
        log.debug("Enhanced retrieval started: query={}", query.getText());

        // 1. 确定要检索的层级
        List<String> layers = determineLayers(query);
        // 2. 确定要检索的域
        List<String> domains = query.getAllDomains();

        // 3. 并行检索各层
        List<CompletableFuture<List<MemoryRecord>>> futures = new ArrayList<>();
        for (String layer : layers) {
            futures.add(CompletableFuture.supplyAsync(() -> retrieveFromLayer(layer, query, domains), executor));
        }

        // 4. 等待并合并结果
        List<MemoryRecord> allHits = futures.stream()
                .flatMap(f -> f.join().stream())
                .toList();

        // 5. 跨域融合（如果是多域查询）
        if (query.isCrossDomain()) {
            allHits = fusionEngine.fuse(allHits, query);
        }

        // 6. 重排序、去重、截断
        List<MemoryRecord> finalHits = rerankAndTruncate(allHits, query.getMaxResults());

        // 7. 更新检索计数
        finalHits.forEach(MemoryRecord::incrementRetrieval);

        long took = System.currentTimeMillis() - start;
        log.debug("Enhanced retrieval completed: hits={}, took={}ms", finalHits.size(), took);

        return finalHits.stream().map(MemoryRecord::toMemoryEntry).toList();
    }

    /**
     * 确定要检索的层级列表。
     */
    private List<String> determineLayers(MemoryQuery query) {
        List<String> layers = new ArrayList<>();
        if (query.isIncludeWorking() && !query.getSessionId().isEmpty()) {
            layers.add("working");
        }
        if (query.isIncludeEpisodic()) {
            layers.add("episodic");
        }
        if (query.isIncludeSemantic()) {
            layers.add("semantic");
        }
        if (query.isIncludeProcedural()) {
            layers.add("procedural");
        }
        if (query.isIncludeCollective()) {
            layers.add("collective");
        }
        return layers;
    }

    /**
     * 从指定层级检索。
     */
    // 在 retrieveFromLayer 方法中，对情景记忆特殊处理
    private List<MemoryRecord> retrieveFromLayer(String layer, MemoryQuery query, List<String> domains) {
        if ("episodic".equals(layer)) {
            // 情景记忆支持 sessionId 过滤
            Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
            Instant to = Instant.now();
            List<MemoryRecord> results = new ArrayList<>();
            for (String domain : domains) {
                results.addAll(episodicMemory.queryByTimeRange(
                        query.getUserId(),
                        domain,
                        query.getSessionId(),   // 传递 sessionId
                        from,
                        to,
                        query.getMaxResults()
                ));
            }
            return results;
        }
        // 其他层走双核路由
        return dualCoreRouter.route(layer, query, domains);
    }

    /**
     * 重排序与截断。
     */
    private List<MemoryRecord> rerankAndTruncate(List<MemoryRecord> hits, int maxResults) {
        return hits.stream()
                .sorted((a, b) -> {
                    // 综合排序：效用评分 > 安全评分 > 强度 > 时间
                    double scoreA = a.getUtilityScore() * 0.4 + a.getSafetyScore() * 0.3 + a.getStrength() * 0.2;
                    double scoreB = b.getUtilityScore() * 0.4 + b.getSafetyScore() * 0.3 + b.getStrength() * 0.2;
                    if (scoreA != scoreB) return Double.compare(scoreB, scoreA);
                    return b.getTimestamp().compareTo(a.getTimestamp());
                })
                .distinct()
                .limit(maxResults)
                .toList();
    }
}