package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.memory.config.AeonMemoryProperties;
import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.storage.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final EmbeddingService embeddingService; // 新增字段
    private final Neo4jGraphService graphService;

    private final LuceneIndexService luceneIndexService;

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
        // 仅当 sessionId 非空时才检索工作记忆
        if (query.isIncludeWorking() && query.getSessionId() != null && !query.getSessionId().isEmpty()) {
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

    // EnhancedRetrievalOrchestrator.java

    public Map<String, Object> debugRetrieve(MemoryQuery query) {
        long start = System.currentTimeMillis();
        List<String> domains = query.getAllDomains();
        String userId = query.getUserId();
        String sessionId = query.getSessionId();
        int maxResults = query.getMaxResults();

//        // 1. 热存储 = 情景记忆（按时间范围查询，同分层浏览使用的途径）
//        List<MemoryRecord> hotHits = new ArrayList<>();
//        if (query.isIncludeEpisodic()) {
//            Instant from = Instant.now().minus(3650, ChronoUnit.DAYS); // 10年范围
//            Instant to = Instant.now();
//            for (String domain : domains) {
//                hotHits.addAll(episodicMemory.queryByTimeRange(
//                        userId, domain, sessionId, from, to, maxResults));
//            }
//        }
//        // 可选：也包含工作记忆（如果 sessionId 不为空）
//        if (query.isIncludeWorking() && sessionId != null && !sessionId.isEmpty()) {
//            hotHits.addAll(workingMemory.queryBySession(sessionId, query.getText(), maxResults));
//        }
        // 1. 工作记忆（真正的热存储）- 目前仅在有 sessionId 时尝试获取
        List<MemoryRecord> workingHits = new ArrayList<>();
        if (sessionId != null && !sessionId.isEmpty() && query.isIncludeWorking()) {
            workingHits = workingMemory.queryBySession(sessionId, query.getText(), maxResults);
        }

        // 2. 情景记忆（按时间范围查询）
        List<MemoryRecord> episodicHits = new ArrayList<>();
        if (query.isIncludeEpisodic()) {
            Instant from = Instant.now().minus(3650, ChronoUnit.DAYS);
            Instant to = Instant.now();
            for (String domain : domains) {
                episodicHits.addAll(episodicMemory.queryByTimeRange(
                        userId, domain, sessionId, from, to, maxResults));
            }
        }


        // 2. Lucene 全文检索（可能为空）
        List<MemoryRecord> luceneHits = new ArrayList<>();
        if (query.getText() != null && !query.getText().isBlank()) {
            List<String> luceneIds = luceneIndexService.searchEpisodic(query.getText(), maxResults);
            luceneHits = luceneIds.stream()
                    .map(episodicMemory::findById)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        // 3. 向量检索（文本语义搜索）
        List<MemoryRecord> vectorHits = new ArrayList<>();
        if (query.getText() != null && !query.getText().isBlank()) {
            vectorHits = semanticMemory.semanticSearch(query.getText(), maxResults);
        }

        // 4. 图谱扩展（基于 Lucene 种子）
        List<MemoryRecord> graphHits = new ArrayList<>();
        if (graphService != null && !luceneHits.isEmpty()) {
            List<String> seedIds = luceneHits.stream().map(MemoryRecord::getId).collect(Collectors.toList());
            List<String> expandedIds = graphService.expandByRelations(seedIds);
            if (expandedIds != null && !expandedIds.isEmpty()) {
                graphHits = expandedIds.stream()
                        .map(semanticMemory::findById)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        }

        // 合并所有通路用于融合（现在包含工作记忆和情景记忆）
        List<MemoryRecord> all = new ArrayList<>();
        all.addAll(workingHits);
        all.addAll(episodicHits);
        all.addAll(luceneHits);
        all.addAll(vectorHits);
        all.addAll(graphHits);
        List<MemoryRecord> fused = fusionEngine.fuse(all.stream().distinct().collect(Collectors.toList()), query);

        long took = System.currentTimeMillis() - start;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workingHits", toEntryList(workingHits));
        result.put("episodicHits", toEntryList(episodicHits));
        result.put("luceneHits", toEntryList(luceneHits));
        result.put("vectorHits", toEntryList(vectorHits));
        result.put("graphHits", toEntryList(graphHits));
        result.put("fusedHits", toEntryList(fused));
        result.put("tookMs", took);
        return result;
    }




    private List<Map<String, Object>> toEntryList(List<MemoryRecord> records) {
        return records.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>(r.toMemoryEntryAsMap()); // 需在 MemoryEntry 中添加 toMap
            map.put("_score", r.getStrength()); // 临时使用 strength 作为分数
            map.put("_source", r.getLayer());
            return map;
        }).toList();
    }
}