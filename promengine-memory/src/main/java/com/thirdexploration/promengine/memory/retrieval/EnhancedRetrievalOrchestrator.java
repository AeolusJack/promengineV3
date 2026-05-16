package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.memory.config.AeonMemoryProperties;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.storage.*;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 增强检索编排器，负责协调多层、多域检索，并融合结果。
 * 优化：线程池关闭、debug复用主逻辑、时间范围参数、去重提前。
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
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public List<MemoryEntry> retrieve(MemoryQuery query) {
        long start = System.currentTimeMillis();
        log.debug("Enhanced retrieval started: query={}", query.getText());

        List<String> layers = determineLayers(query);
        List<String> domains = query.getAllDomains();

        List<CompletableFuture<List<MemoryRecord>>> futures = new ArrayList<>();
        for (String layer : layers) {
            futures.add(CompletableFuture.supplyAsync(() -> retrieveFromLayer(layer, query, domains), executor));
        }

        List<MemoryRecord> allHits = futures.stream()
                .flatMap(f -> f.join().stream())
                .distinct()
                .collect(Collectors.toList());

        if (query.isCrossDomain()) {
            allHits = fusionEngine.fuse(allHits, query);
        }

        List<MemoryRecord> finalHits = rerankAndTruncate(allHits, query.getMaxResults());
        finalHits.forEach(MemoryRecord::incrementRetrieval);

        long took = System.currentTimeMillis() - start;
        log.debug("Retrieval completed: hits={}, took={}ms", finalHits.size(), took);
        return finalHits.stream().map(MemoryRecord::toMemoryEntry).collect(Collectors.toList());
    }

    private List<String> determineLayers(MemoryQuery query) {
        List<String> layers = new ArrayList<>();
        if (query.isIncludeWorking() && query.getSessionId() != null && !query.getSessionId().isEmpty()) {
            layers.add("working");
        }
        if (query.isIncludeEpisodic()) layers.add("episodic");
        if (query.isIncludeSemantic()) layers.add("semantic");
        if (query.isIncludeProcedural()) layers.add("procedural");
        if (query.isIncludeCollective()) layers.add("collective");
        return layers;
    }

    private List<MemoryRecord> retrieveFromLayer(String layer, MemoryQuery query, List<String> domains) {
        if ("episodic".equals(layer)) {
            Instant from = query.getFromTime() != null ? query.getFromTime() : Instant.now().minus(30, ChronoUnit.DAYS);
            Instant to = query.getToTime() != null ? query.getToTime() : Instant.now();
            List<MemoryRecord> results = new ArrayList<>();
            for (String domain : domains) {
                results.addAll(episodicMemory.queryByTimeRange(
                        query.getUserId(), domain, query.getSessionId(),
                        from, to, query.getMaxResults(), query.getProjectId()));
            }
            return results;
        }
        return dualCoreRouter.route(layer, query, domains);
    }

    private List<MemoryRecord> rerankAndTruncate(List<MemoryRecord> hits, int maxResults) {
        return hits.stream()
                .sorted((a, b) -> {
                    double scoreA = a.getUtilityScore() * 0.4 + a.getSafetyScore() * 0.3 + a.getStrength() * 0.2;
                    double scoreB = b.getUtilityScore() * 0.4 + b.getSafetyScore() * 0.3 + b.getStrength() * 0.2;
                    if (scoreA != scoreB) return Double.compare(scoreB, scoreA);
                    return b.getTimestamp().compareTo(a.getTimestamp());
                })
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    // 复用主逻辑的 debug 方法
    public Map<String, Object> debugRetrieve(MemoryQuery query) {
        long start = System.currentTimeMillis();
        List<MemoryRecord> allHits = retrieve(query).stream()
                .map(entry -> {
                    // 简单转换，实际项目中可用 mapper
                    MemoryRecord record = MemoryRecord.builder().build();
                    record.setId(entry.getId());
                    record.setContent(entry.getContent());
                    record.setDomain(entry.getDomain());
                    record.setLayer(entry.getLayer());
                    return record;
                })
                .collect(Collectors.toList());
        long took = System.currentTimeMillis() - start;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hits", allHits);
        result.put("count", allHits.size());
        result.put("tookMs", took);
        return result;
    }
}