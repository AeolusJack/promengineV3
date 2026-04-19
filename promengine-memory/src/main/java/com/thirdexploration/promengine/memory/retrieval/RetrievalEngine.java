package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.core.domain.Query;
import com.thirdexploration.promengine.core.domain.RetrievalStrategy;
import com.thirdexploration.promengine.core.domain.SearchResult;
import com.thirdexploration.promengine.memory.config.MemoryProperties;
import com.thirdexploration.promengine.memory.model.StoredMemoryEntry;
import com.thirdexploration.promengine.memory.storage.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

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

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

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
        CompletableFuture<List<SearchResult.MemoryHit>> vectorFuture = CompletableFuture.supplyAsync(
                () -> searchVector(queryVector, strategy.getTopK()), executor);

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