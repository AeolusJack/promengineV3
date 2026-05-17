package com.thirdexploration.promengine.memory.api;

import com.thirdexploration.promengine.memory.config.AeonMemoryProperties;
import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.evolution.TAMEEvaluator;
import com.thirdexploration.promengine.memory.model.*;
import com.thirdexploration.promengine.memory.retrieval.EmbeddingService;
import com.thirdexploration.promengine.memory.retrieval.EnhancedRetrievalOrchestrator;
import com.thirdexploration.promengine.memory.retrieval.LuceneIndexService;
import com.thirdexploration.promengine.memory.storage.*;
import com.thirdexploration.promengine.memory.util.MemoryDeduplicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 统一记忆 API 实现，整合所有记忆层。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedMemoryAPIImpl implements UnifiedMemoryAPI {

    private final WorkingMemoryManager workingMemory;
    private final EpisodicMemoryService episodicMemory;
    private final SemanticMemoryService semanticMemory;
    private final ProceduralMemoryService proceduralMemory;
    private final CollectiveMemoryService collectiveMemory;

    private final EnhancedRetrievalOrchestrator retrievalOrchestrator;
    private final MemoryMetadataRegistry registry;
    private final TAMEEvaluator tameEvaluator;
    private final MemoryDeduplicator deduplicator;
    private final EmbeddingService embeddingService;
    private final AeonMemoryProperties properties;

    private final LuceneIndexService luceneIndexService;
    @Override
    @Transactional
    public void remember(String content, MemoryMetadata metadata) {
        if (!shouldStore(content)) return;

        MemoryRecord record = buildRecord(content, metadata);
        tameEvaluator.evaluateAndEnrich(record);

        String layer = determineLayer(metadata);
        record.setLayer(layer);

        // ---- 生成向量（如果未提供）----
        if (record.getVector() == null) {
            record.setVector(embeddingService.embed(content));
        }

        // ---- 更新 Lucene 索引（针对情景/语义层）----
        if ("episodic".equals(layer)) {
            luceneIndexService.indexEpisodic(record.getId(), record.getContent(), record.getSummary());
        } else if ("semantic".equals(layer)) {
            luceneIndexService.indexSemantic(record.getId(), record.getContent(), record.getSummary());
        }

        // 分层存储
        switch (layer) {
            case "working" -> workingMemory.put(metadata.getSessionId(), record);
            case "episodic" -> episodicMemory.store(record);
            case "semantic" -> semanticMemory.store(record);
            case "procedural" -> proceduralMemory.store(record);
            case "collective" -> collectiveMemory.store(record);
        }
    }

    @Override
    public void remember(MemoryEntry entry) {
        // 跳过重要性过低的内容（如临时提示、中间结果）
        if (entry.getImportance() < 0.3f) {
            log.debug("Skip memory with low importance: {}", entry.getImportance());
            return;
        }
        MemoryMetadata metadata = MemoryMetadata.builder()
                .userId(entry.getUserId())
                .domain(entry.getDomain())
                .importance(entry.getImportance())
                .build();
        remember(entry.getContent(), metadata);
    }

    @Override
    public List<MemoryEntry> recall(MemoryQuery query) {
        return retrievalOrchestrator.retrieve(query);
    }

    @Override
    public CompletableFuture<List<MemoryEntry>> recallAsync(MemoryQuery query) {
        return CompletableFuture.supplyAsync(() -> recall(query));
    }

    @Override
    public void forget(String memoryId, boolean permanent) {
        // 尝试在各层删除
        episodicMemory.softDelete(memoryId);
        semanticMemory.softDelete(memoryId);
        proceduralMemory.softDelete(memoryId);
        // 集体记忆暂不支持单独遗忘
        log.info("Forgot memory: id={}, permanent={}", memoryId, permanent);
    }

    @Override
    public void updateStrength(String memoryId, float newStrength) {
        episodicMemory.updateStrength(memoryId, newStrength);
        semanticMemory.updateStrength(memoryId, newStrength);
    }

//    @Override
//    public MemoryStats getStats(String userId, String domain) {
//        Map<String, Long> layerCounts = new HashMap<>();
//        // 简化实现
//        return MemoryStats.builder()
//                .userId(userId)
//                .domain(domain)
//                .layerCounts(layerCounts)
//                .totalRecords(0)
//                .build();
//    }

    @Override
    public void promoteWorkingToEpisodic(String sessionId) {
        List<MemoryRecord> workingRecords = workingMemory.clearSession(sessionId);
        for (MemoryRecord record : workingRecords) {
            record.setLayer("episodic");
            episodicMemory.store(record);
        }
        log.info("Promoted {} working memories to episodic for session {}", workingRecords.size(), sessionId);
    }

    @Override
    public void reflect() {
        log.info("Starting memory reflection...");
        // 调用蒸馏等后台任务
        log.info("Memory reflection completed");
    }

    private boolean shouldStore(String content) {
        if (properties.getRetrieval().isDeduplicationEnabled() && deduplicator.isDuplicate(content)) {
            return false;
        }
        return true;
    }

    private MemoryRecord buildRecord(String content, MemoryMetadata metadata) {
        String domain = metadata.getDomain() != null ? metadata.getDomain() : registry.getDefaultDomain();
        String sharingLevel = metadata.getSharingLevel() != null ? metadata.getSharingLevel() : registry.getDefaultSharingLevel();

        return MemoryRecord.builder()
                .id(generateId())
                .userId(metadata.getUserId())
                .content(content)
                .summary(truncate(content, 200))
                .timestamp(Instant.now())
                .lastAccessed(Instant.now())
                .memoryType("episodic")
                .importance(metadata.getImportance())
                .metadata(metadata.getExtra())
                .domain(domain)
                .projectId(metadata.getProjectId())
                .strength(1.0)
                .utilityScore(0.5)
                .safetyScore(0.9)
                .sharingLevel(sharingLevel)
                .provenance(Provenance.userInput(metadata.getUserId())) //如果需要在工具调用后存储记忆，可使用.toolOutput("calculator"),如果后续实现知识蒸馏，可使用：.distilled("system", List.of("mem_123", "mem_456"))
                .retrievalCount(0)
                .sessionId(metadata.getSessionId())   // 新增
                .build();
    }

    private String determineLayer(MemoryMetadata metadata) {
        if (metadata.getLayerHint() != null) {
            return metadata.getLayerHint();
        }
        if (metadata.getImportance() > 0.8) {
            return "semantic";
        }
        return "episodic";
    }

    private String generateId() {
        return "mem_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }

    @Override
    public MemoryStats getStats(String userId, String domain) {
        long total;
        if (userId == null) {
            total = episodicMemory.countAll() + semanticMemory.countAll() + proceduralMemory.countAll();
        } else {
            total = episodicMemory.countByUser(userId) + semanticMemory.countByUser(userId);
        }
        return MemoryStats.builder()
                .userId(userId)
                .domain(domain)
                .totalRecords(total)
                .build();
    }
}