package com.thirdexploration.promengine.memory.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.MemoryService;
import com.thirdexploration.promengine.core.domain.*;
import com.thirdexploration.promengine.core.util.IdGenerator;
import com.thirdexploration.promengine.memory.config.MemoryProperties;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.model.StoredMemoryEntry;
import com.thirdexploration.promengine.memory.retrieval.LuceneIndexService;
import com.thirdexploration.promengine.memory.retrieval.RetrievalEngine;
import com.thirdexploration.promengine.memory.storage.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * MemoryService 默认实现，整合热、温、冷、向量存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultMemoryService implements MemoryService {

    private final HotStorage hotStorage;
    private final WarmStorage warmStorage;
    private final ColdStorage coldStorage;
    private final VectorStorage vectorStorage;
    private final LuceneIndexService luceneIndexService;
    private final RetrievalEngine retrievalEngine;
    private final MemoryProperties properties;
    private final EmbeddingService embeddingService; // 假设有嵌入服务
    private final MemoryWarmupService warmupService;
    private final ObjectMapper objectMapper;
    @Override
    @Transactional
    public void store(MemoryEntry entry) {
        String id = entry.getId() != null ? entry.getId() : IdGenerator.generateWithPrefix("mem");
        Instant now = Instant.now();

        // 1. 生成摘要（若用户未提供，则自动截取前200字符）
        String summary = entry.getMetadata() != null && entry.getMetadata().containsKey("summary")
                ? entry.getMetadata().get("summary").toString()
                : truncateText(entry.getContent(), 200);

        // 2. 使用摘要生成向量（用于语义检索）
        float[] vector = embeddingService.embed(summary);

        // 3. 构建存储记录
        MemoryRecord record = MemoryRecord.builder()
                .id(id)
                .userId(entry.getUserId())
                .content(entry.getContent())          // 完整内容
                .summary(summary)                     // 摘要
                .timestamp(entry.getTimestamp() != null ? entry.getTimestamp() : now)
                .memoryType(entry.getType().name())
                .importance(entry.getImportance())
                .metadata(entry.getMetadata())
                .vector(vector)
                .ttlSeconds(entry.getTtlSeconds())
                .deleted(false)
                .build();

        // 4. 写入热存储（SQLite，用于快速访问和关键词搜索）
        hotStorage.insert(record);

        // 5. 写入向量存储（基于摘要向量）
        try {
            vectorStorage.add(id, vector, objectMapper.writeValueAsString(Map.of("summary", summary)));
        } catch (Exception e) {
            log.warn("Vector storage write failed for id={}, continuing", id, e);
        }

//        检索时如何使用摘要向量
//        RetrievalEngine 中的向量检索已经使用摘要向量进行相似度计算，无需修改。当返回结果时，可通过 memoryId 从热存储或温存储读取完整内容。
//
//// 在 RetrievalEngine.searchVector 中，获取到 ID 列表后：
//        List<String> ids = hits.stream().map(VectorStorage.SearchHit::id).toList();
//        List<StoredMemoryEntry> entries = ids.stream()
//                .map(hotStorage::findById)   // 从热存储读取完整内容
//                .filter(Objects::nonNull)
//                .toList();

        // 6. 异步更新 Lucene 索引（基于摘要）
        CompletableFuture.runAsync(() -> luceneIndexService.index(
                id, entry.getUserId(), record.getTimestamp().toEpochMilli(), summary));

        log.info("Stored memory id={}, summary length={}, full content length={}",
                id, summary.length(), entry.getContent().length());
    }

    // 辅助截断方法
    private String truncateText(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }

    @Override
    public SearchResult retrieve(Query query, RetrievalStrategy strategy) {
        float[] queryVector = embeddingService.embed(query.getText());
        return retrievalEngine.retrieve(query, strategy, queryVector);
    }
    @Override
    public Pair<SearchResult, RetrievalDetails> retrieveWithDetails(Query query, RetrievalStrategy strategy) {
        // 如果没有传入向量，则通过文本生成
        float[] vector = embeddingService.embed(query.getText());

        // 调用检索引擎获取带详情的原始结果

        Pair<SearchResult, RetrievalEngine.FusionDetails> fusionDetailsPair = retrievalEngine.retrieveWithDetails(query, strategy, vector);

        SearchResult result = fusionDetailsPair.left();
        RetrievalEngine.FusionDetails fusionDetails = fusionDetailsPair.right();

        // 将内部 FusionDetails 转换为对外暴露的 RetrievalDetails（避免暴露内部类）
        RetrievalDetails details = new RetrievalDetailsAdapter(fusionDetails);

        return Pair.of(result, details);
    }

    /**
     * 适配器，将内部 FusionDetails 转换为 MemoryService.RetrievalDetails
     */
    private record RetrievalDetailsAdapter(RetrievalEngine.FusionDetails fusionDetails)
            implements RetrievalDetails {
        @Override
        public List<SearchResult.MemoryHit> getHotHits() { return fusionDetails.getHotHits(); }
        @Override
        public List<SearchResult.MemoryHit> getWarmSummaryHits() { return fusionDetails.getWarmSummaryHits(); }
        @Override
        public List<SearchResult.MemoryHit> getLuceneHits() { return fusionDetails.getLuceneHits(); }
        @Override
        public List<SearchResult.MemoryHit> getVectorHits() { return fusionDetails.getVectorHits(); }
        @Override
        public List<SearchResult.MemoryHit> getFusedHits() { return fusionDetails.getFusedHits(); }
        @Override
        public long getTookMs() { return fusionDetails.getTookMs(); }
    }

    @Override
    public void forget(String memoryId, boolean permanent) {
        if (permanent) {
            hotStorage.hardDelete(memoryId);
            vectorStorage.delete(memoryId);
            luceneIndexService.delete(memoryId);
            log.info("Permanently deleted memory id={}", memoryId);
        } else {
            hotStorage.softDelete(memoryId);
            // 软删除后向量仍保留，但在检索时过滤
            log.info("Soft deleted memory id={}", memoryId);
        }
    }

    /**
     * 统计当前记忆总数（热存储中未删除的记录数）。
     *
     * @return 记忆条目数量
     */
    public long count() {
        // 调用 HotStorage 的计数方法
        return hotStorage.countActive();
    }
    @Override
    @Async
    public void reflect() {
        log.info("Starting memory reflection task");
        // 后台归纳：从热存储提取高频模式，生成摘要规则
        // 具体实现可调用 LLM 进行归纳，此处简化为日志
        log.info("Memory reflection completed");
    }

    @Override
    public WarmupStatus getWarmupStatus() {
        return warmupService.getStatus();
    }

    // 迁移任务（可由定时任务触发）
    @Scheduled(cron = "0 0 2 * * ?")
    public void migrateHotToWarm() {
        Instant cutoff = Instant.now().minus(properties.getHotRetention());
        List<StoredMemoryEntry> toMigrate = hotStorage.migrateToWarm(cutoff);
        if (toMigrate.isEmpty()) return;

        String partitionMonth = java.time.YearMonth.now().toString();
        warmStorage.append(toMigrate, partitionMonth);
        // 从热存储硬删除已迁移记录
        for (StoredMemoryEntry e : toMigrate) {
            hotStorage.hardDelete(e.getId());
        }
        log.info("Migrated {} records from hot to warm", toMigrate.size());
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void migrateWarmToCold() {
        Instant cutoff = Instant.now().minus(properties.getWarmRetention());
        List<String> oldPartitions = warmStorage.listPartitions().stream()
                .filter(p -> p.compareTo(java.time.YearMonth.from(cutoff).toString()) < 0)
                .toList();
        for (String partition : oldPartitions) {
            warmStorage.archiveToCold(partition, coldStorage);
        }
    }
}