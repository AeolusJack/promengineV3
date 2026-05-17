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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    private final ChatClient.Builder chatClientBuilder;

    // 构造器注入（通过 @RequiredArgsConstructor 自动加入）
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
        log.info("Starting memory reflection (distillation)...");
        try {
            // 1. 获取近7天、重要性>0.6的情景记忆
            List<MemoryRecord> episodicRecords = episodicMemory.queryByTimeRange(
                    null, "general", null,
                    Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS),
                    Instant.now(),
                    200, null
            );
            if (episodicRecords.isEmpty()) return;

            // 2. 按会话分组（取前10个会话，避免任务过重）
            Map<String, List<MemoryRecord>> grouped = episodicRecords.stream()
                    .filter(r -> r.getSessionId() != null)
                    .collect(Collectors.groupingBy(MemoryRecord::getSessionId));
            int count = 0;
            for (Map.Entry<String, List<MemoryRecord>> entry : grouped.entrySet()) {
                if (count++ > 10) break;
                List<MemoryRecord> records = entry.getValue();
                if (records.size() < 3) continue;

                String combined = records.stream()
                        .map(MemoryRecord::getContent)
                        .collect(Collectors.joining("\n---\n"));
                String summary = summarizeWithLLM(combined);
                if (summary == null || summary.isBlank()) continue;

                // 创建语义记忆
                MemoryRecord semRecord = MemoryRecord.builder()
                        .id("sem_reflect_" + UUID.randomUUID().toString().replace("-", ""))
                        .userId(records.get(0).getUserId())
                        .content(summary)
                        .summary(truncate(summary, 200))
                        .memoryType("semantic")
                        .layer("semantic")
                        .domain("general")
                        .importance(0.85f)
                        .strength(1.0)
                        .utilityScore(0.8)
                        .safetyScore(0.9)
                        .timestamp(Instant.now())
                        .provenance(Provenance.distilled("system", records.stream().map(MemoryRecord::getId).toList()))
                        .build();
                semanticMemory.store(semRecord);

                // 可选：降低原始情景记忆强度（而非直接删除）
                records.forEach(r -> episodicMemory.updateStrength(r.getId(), 0.3f));
            }
        } catch (Exception e) {
            log.error("Reflection failed", e);
        }
        log.info("Memory reflection completed");
    }

    /**
     * 调用本地 Ollama 轻量模型生成摘要
     */
    private String summarizeWithLLM(String content) {
        if (content.length() < 100) return content; // 太短不需要摘要
        try {
            ChatClient client = chatClientBuilder.build();
            String prompt = "请用一段话总结以下对话内容的核心要点（不超过150字）：\n" +
                    content.substring(0, Math.min(content.length(), 3000));
            String result = client.prompt(prompt).call().content();
            return result != null ? result.trim() : "";
        } catch (Exception e) {
            log.warn("LLM summarization failed, falling back to truncation: {}", e.getMessage());
            return content.length() > 200 ? content.substring(0, 200) + "..." : content;
        }
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