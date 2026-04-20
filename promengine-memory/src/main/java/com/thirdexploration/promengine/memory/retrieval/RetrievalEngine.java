package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.core.domain.Pair;
import com.thirdexploration.promengine.core.domain.Query;
import com.thirdexploration.promengine.core.domain.RetrievalStrategy;
import com.thirdexploration.promengine.core.domain.SearchResult;
import com.thirdexploration.promengine.memory.config.MemoryProperties;
import com.thirdexploration.promengine.memory.config.MemoryRetrievalPolicyProperties;
import com.thirdexploration.promengine.memory.model.StoredMemoryEntry;
import com.thirdexploration.promengine.memory.storage.*;
import com.thirdexploration.promengine.model.routing.ComplexityEvaluator;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.apache.hadoop.thirdparty.org.checkerframework.checker.units.UnitsTools.K;

/**
 * 多路融合检索引擎。
 * 并行查询热存储、温存储（摘要+Lucene）、向量存储，然后使用 RRF 融合排序。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalEngine {

    private final HotStorage hotStorage;
    private final WarmStorage warmStorage;
    private final ColdStorage coldStorage;
    private final VectorStorage vectorStorage;
    private final LuceneIndexService luceneIndexService;
    private final MemoryProperties properties;
    private final ComplexityEvaluator complexityEvaluator;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final MemoryRetrievalPolicyProperties policyProps;
    /**
     * 执行检索，返回融合后的结果。
     */
    public SearchResult retrieve(Query query, RetrievalStrategy strategy, float[] queryVector) {
        long start = System.currentTimeMillis();
        String userId = query.getUserId();
        Instant now = Instant.now();
        Instant timeFrom = now.minus(strategy.getTimeWindow());
        Instant timeTo = now;

        // 并行执行各通路检索
        CompletableFuture<List<SearchResult.MemoryHit>> hotFuture = CompletableFuture.supplyAsync(
                () -> searchHot(userId, query.getText(), timeFrom, timeTo, strategy.getTopK()), executor);
        CompletableFuture<List<SearchResult.MemoryHit>> warmSummaryFuture = CompletableFuture.supplyAsync(
                () -> searchWarmSummary(userId, timeFrom, timeTo, strategy.getTopK()), executor);
        CompletableFuture<List<SearchResult.MemoryHit>> luceneFuture = CompletableFuture.supplyAsync(
                () -> searchLucene(userId, query.getText(), strategy.getTopK()), executor);

//        CompletableFuture<List<SearchResult.MemoryHit>> vectorFuture = CompletableFuture.supplyAsync(
//                () -> searchVector(queryVector, strategy.getTopK()), executor);
        CompletableFuture<List<SearchResult.MemoryHit>> vectorFuture = CompletableFuture.supplyAsync(
                () -> searchVectorByText(query.getText(), strategy.getTopK()), executor);



        CompletableFuture<List<SearchResult.MemoryHit>> combined = CompletableFuture.allOf(
                hotFuture, warmSummaryFuture, luceneFuture, vectorFuture)
                .thenApply(v -> {
                    List<SearchResult.MemoryHit> allHits = new ArrayList<>();
                    allHits.addAll(hotFuture.join());
                    allHits.addAll(warmSummaryFuture.join());
                    allHits.addAll(luceneFuture.join());
                    allHits.addAll(vectorFuture.join());
                    return allHits;
                });

        List<SearchResult.MemoryHit> fusedHits;
        try {
            List<SearchResult.MemoryHit> allHits = combined.join();
            fusedHits = RRFusion.fuse(allHits, strategy.getTopK());
        } catch (Exception e) {
            log.error("Retrieval fusion failed", e);
            fusedHits = Collections.emptyList();
        }

        // 如果结果不足且允许扫描冷存储
        if (fusedHits.size() < strategy.getTopK() && strategy.isAllowColdStorageScan()) {
            log.info("Insufficient results ({}), scanning cold storage", fusedHits.size());
            List<SearchResult.MemoryHit> coldHits = searchCold(userId, query.getText(), strategy.getTopK() - fusedHits.size());
            fusedHits.addAll(coldHits);
        }

        long took = System.currentTimeMillis() - start;
        return SearchResult.builder()
                .hits(fusedHits)
                .totalHits(fusedHits.size())
                .tookMs(took)
                .build();
    }

    /**
     * 执行多路检索并返回融合结果及详情。
     *
     * @param query        查询条件
     * @param strategy     检索策略
     * @param queryVector  查询向量（可为 null，用于向量通路）
     * @return Pair 包含 SearchResult 和 FusionDetails
     */
    public Pair<SearchResult, FusionDetails> retrieveWithDetails(Query query, RetrievalStrategy strategy, float[] queryVector) {
        long start = System.currentTimeMillis();
        String userId = query.getUserId();
        String queryText = query.getText();
        Instant now = Instant.now();
        Instant timeFrom = now.minus(strategy.getTimeWindow());
        Instant timeTo = now;

        // 解析有效策略（可能根据复杂度覆盖）
        MemoryRetrievalPolicyProperties effectivePolicy = resolveEffectivePolicy(query);

        // 用于存储各通路的结果
        // 使用 AtomicReference 存储各通路结果
        AtomicReference<List<SearchResult.MemoryHit>> hotHitsRef = new AtomicReference<>(List.of());
        AtomicReference<List<SearchResult.MemoryHit>> warmHitsRef = new AtomicReference<>(List.of());
        AtomicReference<List<SearchResult.MemoryHit>> luceneHitsRef = new AtomicReference<>(List.of());
        AtomicReference<List<SearchResult.MemoryHit>> vectorHitsRef = new AtomicReference<>(List.of());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

            // 热存储通路
        if (effectivePolicy.isHotEnabled()) {
            futures.add(CompletableFuture.runAsync(() -> {
                List<SearchResult.MemoryHit> hits = searchHot(userId, queryText, timeFrom, timeTo, effectivePolicy.getHotTopK());
                hotHitsRef.set(hits);
            }, executor));
        }

            // 温存储通路
        if (effectivePolicy.isWarmEnabled()) {
            futures.add(CompletableFuture.runAsync(() -> {
                List<SearchResult.MemoryHit> hits = searchWarmSummary(userId, timeFrom, timeTo, effectivePolicy.getWarmTopK());
                warmHitsRef.set(hits);
            }, executor));
        }

        // Lucene 通路
        if (effectivePolicy.isLuceneEnabled()) {
            futures.add(CompletableFuture.runAsync(() -> {
                List<SearchResult.MemoryHit> hits = searchLucene(userId, queryText, effectivePolicy.getLuceneTopK());
                luceneHitsRef.set(hits);
            }, executor));
        }

        // 向量通路
        if (effectivePolicy.isVectorEnabled() && queryVector != null) {
            futures.add(CompletableFuture.runAsync(() -> {
                List<SearchResult.MemoryHit> hits = searchVectorByText(queryText, effectivePolicy.getVectorTopK());
                vectorHitsRef.set(hits);
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<SearchResult.MemoryHit> hotHits = hotHitsRef.get();
        List<SearchResult.MemoryHit> warmHits = warmHitsRef.get();
        List<SearchResult.MemoryHit> luceneHits = luceneHitsRef.get();
        List<SearchResult.MemoryHit> vectorHits = vectorHitsRef.get();
        // 收集所有命中的排名信息（带权重）
        List<RankedHit> allRankedHits = new ArrayList<>();
        addRankedHits(allRankedHits, hotHits, effectivePolicy.getHotWeight());
        addRankedHits(allRankedHits, warmHits, effectivePolicy.getWarmWeight());
        addRankedHits(allRankedHits, luceneHits, effectivePolicy.getLuceneWeight());
        addRankedHits(allRankedHits, vectorHits, effectivePolicy.getVectorWeight());

      // RRF 融合
        List<SearchResult.MemoryHit> fusedHits = RRFusion.fuseWithWeights(allRankedHits, effectivePolicy.getFusionTopK());

        // 如果结果不足且允许扫描冷存储
        if (fusedHits.size() < strategy.getTopK() && strategy.isAllowColdStorageScan()) {
            List<SearchResult.MemoryHit> coldHits = searchCold(userId, queryText, strategy.getTopK() - fusedHits.size());
            fusedHits.addAll(coldHits);
        }

        long took = System.currentTimeMillis() - start;

        // 构建融合详情对象
        FusionDetails details = FusionDetails.builder()
                .hotHits(hotHits)
                .warmSummaryHits(warmHits)
                .luceneHits(luceneHits)
                .vectorHits(vectorHits)
                .fusedHits(fusedHits)
                .tookMs(took)
                .effectivePolicy(effectivePolicy)  // 可选
                .build();

        SearchResult result = SearchResult.builder()
                .hits(fusedHits)
                .totalHits(fusedHits.size())
                .tookMs(took)
                .build();
        log.debug("Hot hits: {}, Vector hits: {}, Fused: {}",
                details.getHotHits().size(),
                details.getVectorHits().size(),
                details.getFusedHits().size());
        return Pair.of(result, details);
    }

    private MemoryRetrievalPolicyProperties resolveEffectivePolicy(Query query) {
        if (query.getText() == null) {
            return policyProps;
        }
        double complexity = complexityEvaluator.evaluate(query.getText());
        if (complexity >= 0.7 && policyProps.getComplexityOverrides() != null) {
            MemoryRetrievalPolicyProperties.ComplexityLevelPolicy override =
                    policyProps.getComplexityOverrides().get("HIGH");
            if (override != null) {
                // 创建合并后的策略副本（避免修改原配置）
                return mergePolicyWithOverride(policyProps, override);
            }
        }
        return policyProps;
    }

    private MemoryRetrievalPolicyProperties mergePolicyWithOverride(
            MemoryRetrievalPolicyProperties base,
            MemoryRetrievalPolicyProperties.ComplexityLevelPolicy override) {
        // 使用 BeanUtils 或手动复制，这里简化示意
        MemoryRetrievalPolicyProperties merged = new MemoryRetrievalPolicyProperties();
        // 复制 base 所有属性
        BeanUtils.copyProperties(base, merged);
        // 用 override 的非空值覆盖
        if (override.isHotEnabled()) merged.setHotEnabled(override.isHotEnabled());
        if (override.isWarmEnabled() ) merged.setWarmEnabled(override.isWarmEnabled());
        if (override.isLuceneEnabled() ) merged.setLuceneEnabled(override.isLuceneEnabled());
        if (override.isVectorEnabled() ) merged.setVectorEnabled(override.isVectorEnabled());
        if (override.getHotTopK() != null) merged.setHotTopK(override.getHotTopK());
        if (override.getWarmTopK() != null) merged.setWarmTopK(override.getWarmTopK());
        if (override.getLuceneTopK() != null) merged.setLuceneTopK(override.getLuceneTopK());
        if (override.getVectorTopK() != null) merged.setVectorTopK(override.getVectorTopK());
        if (override.getFusionTopK() != null) merged.setFusionTopK(override.getFusionTopK());
        return merged;
    }


    private void addRankedHits(List<RankedHit> target, List<SearchResult.MemoryHit> hits, double weight) {
        for (int i = 0; i < hits.size(); i++) {
            SearchResult.MemoryHit hit = hits.get(i);
            target.add(new RankedHit(hit, i + 1, weight));
        }
    }
    public static List<SearchResult.MemoryHit> fuseWithWeights(List<RetrievalEngine.RankedHit> rankedHits, int topK) {
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, SearchResult.MemoryHit> docMap = new HashMap<>();

        for (RetrievalEngine.RankedHit rh : rankedHits) {
            SearchResult.MemoryHit hit = rh.hit();
            docMap.putIfAbsent(hit.getMemoryId(), hit);
            double weightedRrf = rh.weight() / (K + rh.rank());
            rrfScores.merge(hit.getMemoryId(), weightedRrf, Double::sum);
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
    // 内部类用于排序
    public record RankedHit(SearchResult.MemoryHit hit, int rank, double weight) {}


    // ========== 内部数据类 ==========

    /**
     * 单个检索通路的结果包装。
     */
    private record PathwayResult(String pathway, List<SearchResult.MemoryHit> hits, double weight) {}

    /**
     * 融合详情 DTO，用于调试和可观测性。
     */
    @Data
    @Builder
    public static class FusionDetails {
        private List<SearchResult.MemoryHit> hotHits;
        private List<SearchResult.MemoryHit> warmSummaryHits;
        private List<SearchResult.MemoryHit> luceneHits;
        private List<SearchResult.MemoryHit> vectorHits;
        private List<SearchResult.MemoryHit> fusedHits;
        private long tookMs;
        private MemoryRetrievalPolicyProperties effectivePolicy; // 添加此字段

    }




    private List<SearchResult.MemoryHit> searchVectorByText(String queryText, int limit) {
        if (queryText == null || queryText.isBlank()) return List.of();
        try {
            // 强制转换为 ChromaVectorStorage 以调用 text search
            if (!(vectorStorage instanceof ChromaVectorStorage chromaStorage)) {
                log.warn("VectorStorage is not ChromaVectorStorage, skipping vector search");
                return List.of();
            }
            List<VectorStorage.SearchHit> hits = chromaStorage.search(queryText, limit);
            if (hits.isEmpty()) return List.of();

            List<String> ids = hits.stream().map(VectorStorage.SearchHit::id).toList();
            return ids.stream()
                    .map(hotStorage::findById)
                    .filter(Objects::nonNull)
                    .map(e -> SearchResult.MemoryHit.builder()
                            .memoryId(e.getId())
                            .content(e.getContent())
                            .timestamp(e.getTimestamp())
                            .score(hits.stream()
                                    .filter(h -> h.id().equals(e.getId()))
                                    .findFirst()
                                    .map(VectorStorage.SearchHit::score)
                                    .orElse(0.7f))
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
            return List.of();
        }
    }
    private List<SearchResult.MemoryHit> searchHot(String userId, String queryText, Instant from, Instant to, int limit) {
        try {
            List<StoredMemoryEntry> entries;
            if (queryText != null && !queryText.isBlank()) {
                entries = hotStorage.searchByKeyword(userId, queryText, limit);
            } else {
                entries = hotStorage.findByTimeRange(userId, from, to, limit);
            }
            return entries.stream()
                    .map(e -> SearchResult.MemoryHit.builder()
                            .memoryId(e.getId())
                            .content(e.getContent())
                            .timestamp(e.getTimestamp())
                            .score(1.0f) // 精确匹配得分高
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Hot storage search failed", e);
            return List.of();
        }
    }

    private List<SearchResult.MemoryHit> searchWarmSummary(String userId, Instant from, Instant to, int limit) {
        try {
            List<StoredMemoryEntry> entries = warmStorage.queryByTimeRange(userId, from, to, limit);
            return entries.stream()
                    .map(e -> SearchResult.MemoryHit.builder()
                            .memoryId(e.getId())
                            .content(e.getContent())
                            .timestamp(e.getTimestamp())
                            .score(0.8f)
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Warm summary search failed", e);
            return List.of();
        }
    }

    private List<SearchResult.MemoryHit> searchLucene(String userId, String queryText, int limit) {
        if (queryText == null || queryText.isBlank()) return List.of();
        try {
            List<String> ids = luceneIndexService.search(userId, queryText, limit);
            if (ids.isEmpty()) return List.of();
            List<StoredMemoryEntry> entries = warmStorage.readFullRecordsByIds(ids);
            return entries.stream()
                    .map(e -> SearchResult.MemoryHit.builder()
                            .memoryId(e.getId())
                            .content(e.getContent())
                            .timestamp(e.getTimestamp())
                            .score(0.9f)
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Lucene search failed", e);
            return List.of();
        }
    }

    private List<SearchResult.MemoryHit> searchVector(float[] queryVector, int limit) {
        if (queryVector == null) return List.of();
        try {
            List<VectorStorage.SearchHit> hits = vectorStorage.search(queryVector, limit);
            if (hits.isEmpty()) return List.of();
            List<String> ids = hits.stream().map(VectorStorage.SearchHit::id).toList();
            // 需要从热/温存储获取完整内容，此处简化：从热存储获取
            List<StoredMemoryEntry> entries = ids.stream()
                    .map(hotStorage::findById)
                    .filter(Objects::nonNull)
                    .toList();
            // 合并相似度分数
            Map<String, Float> scoreMap = hits.stream()
                    .collect(Collectors.toMap(VectorStorage.SearchHit::id, VectorStorage.SearchHit::score));
            return entries.stream()
                    .map(e -> SearchResult.MemoryHit.builder()
                            .memoryId(e.getId())
                            .content(e.getContent())
                            .timestamp(e.getTimestamp())
                            .score(scoreMap.getOrDefault(e.getId(), 0.7f))
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Vector search failed", e);
            return List.of();
        }
    }

    private List<SearchResult.MemoryHit> searchCold(String userId, String queryText, int limit) {
        // 冷存储检索较慢，简单实现关键词匹配
        try {
            List<String> archives = coldStorage.listArchives();
            List<StoredMemoryEntry> allMatches = new ArrayList<>();
            for (String archiveId : archives) {
                // 仅示范：实际应只检索特定时间范围
                List<StoredMemoryEntry> entries = coldStorage.retrieveByIds(Collections.emptyList()); // 需要具体实现
                // 简单过滤
            }
            return allMatches.stream().limit(limit)
                    .map(e -> SearchResult.MemoryHit.builder()
                            .memoryId(e.getId())
                            .content(e.getContent())
                            .timestamp(e.getTimestamp())
                            .score(0.5f)
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Cold storage search failed", e);
            return List.of();
        }
    }
}