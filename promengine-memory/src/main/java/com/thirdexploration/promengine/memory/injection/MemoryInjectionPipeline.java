package com.thirdexploration.promengine.memory.injection;

import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.retrieval.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryInjectionPipeline {

    private final EmbeddingService embeddingService;

    /**
     * 完整的记忆注入预处理：
     * 去重 -> 相关性排序 -> 分层配额 -> 长文本摘要 -> 格式化
     */
    public List<MemoryInjectionItem> process(List<MemoryEntry> memories, String queryText, int maxTotalChars) {
        if (memories.isEmpty()) return List.of();

        // 1. 语义去重
        List<MemoryEntry> deduped = deduplicateBySemantic(memories);

        // 2. 相关性重排序
        List<MemoryEntry> ranked = reRankByRelevance(deduped, queryText);

        // 3. 分层配额分配
        List<MemoryEntry> allocated = allocateQuotaByLayer(ranked, maxTotalChars);

        // 4. 长文本摘要处理
        List<MemoryInjectionItem> items = summarizeLongEntries(allocated);

        return items;
    }

    private List<MemoryEntry> deduplicateBySemantic(List<MemoryEntry> entries) {
        if (entries.size() <= 1) return entries;
        List<MemoryEntry> result = new ArrayList<>();
        // 计算每个记忆的向量
        Map<String, float[]> vectors = new HashMap<>();
        for (MemoryEntry e : entries) {
            float[] vec = embeddingService.embed(e.getSummary() != null ? e.getSummary() : e.getContent());
            if (vec.length > 0) vectors.put(e.getId(), vec);
        }
        Set<String> added = new HashSet<>();
        for (MemoryEntry entry : entries) {
            float[] vec = vectors.get(entry.getId());
            if (vec == null) { result.add(entry); continue; }
            boolean isDuplicate = false;
            for (MemoryEntry existing : result) {
                float[] existVec = vectors.get(existing.getId());
                if (existVec != null && cosineSimilarity(vec, existVec) > 0.95) {
                    isDuplicate = true;
                    // 保留强度较高的
                    if (entry.getStrength() > existing.getStrength()) {
                        result.remove(existing);
                        result.add(entry);
                    }
                    break;
                }
            }
            if (!isDuplicate) result.add(entry);
        }
        return result;
    }

    private List<MemoryEntry> reRankByRelevance(List<MemoryEntry> entries, String queryText) {
        if (queryText == null || queryText.isBlank()) return entries;
        float[] queryVec = embeddingService.embed(queryText);
        if (queryVec.length == 0) return entries;

        // 计算每个记忆与查询的相似度，结合原有强度分数
        List<RankedEntry> ranked = entries.stream().map(e -> {
            float sim = embeddingService.embed(e.getContent()).length > 0 ?
                    cosineSimilarity(queryVec, embeddingService.embed(e.getContent())) : 0f;
            double score = sim * 0.7 + (e.getStrength()) * 0.3;
            return new RankedEntry(e, score);
        }).sorted(Comparator.comparingDouble(RankedEntry::score).reversed()).toList();

        return ranked.stream().map(RankedEntry::entry).collect(Collectors.toList());
    }

    private List<MemoryEntry> allocateQuotaByLayer(List<MemoryEntry> entries, int maxTotalChars) {
        // 分层权重：语义 > 过程 > 情景 > 集体
        Map<String, Double> layerWeights = Map.of(
                "semantic", 0.5,
                "procedural", 0.3,
                "episodic", 0.15,
                "collective", 0.05
        );
        Map<String, Integer> layerCharBudgets = new HashMap<>();
        for (Map.Entry<String, Double> lw : layerWeights.entrySet()) {
            layerCharBudgets.put(lw.getKey(), (int) (maxTotalChars * lw.getValue()));
        }

        List<MemoryEntry> result = new ArrayList<>();
        Map<String, Integer> usedChars = new HashMap<>();
        for (MemoryEntry entry : entries) {
            String layer = entry.getLayer() != null ? entry.getLayer() : "episodic";
            Integer budget = layerCharBudgets.getOrDefault(layer, 0);
            int used = usedChars.getOrDefault(layer, 0);
            String content = entry.getSummary() != null ? entry.getSummary() : entry.getContent();
            int length = content.length();
            if (used + length <= budget) {
                result.add(entry);
                usedChars.put(layer, used + length);
            }
        }
        return result;
    }

    private List<MemoryInjectionItem> summarizeLongEntries(List<MemoryEntry> entries) {
        List<MemoryInjectionItem> items = new ArrayList<>();
        for (MemoryEntry entry : entries) {
            String summary = entry.getSummary();
            if (summary == null || summary.isEmpty()) {
                summary = entry.getContent();
            }
            if (summary.length() > 200) {
                // 简单首尾截断（后续可集成 LLM 摘要）
                summary = summary.substring(0, 100) + " ... " + summary.substring(summary.length() - 100);
            }
            items.add(new MemoryInjectionItem(entry.getId(), entry.getDomain(), entry.getLayer(), entry.getStrength(), summary));
        }
        return items;
    }

    private float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return 0f;
        double dot = 0, normA = 0, normB = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0f;
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private record RankedEntry(MemoryEntry entry, double score) {}

    public record MemoryInjectionItem(String id, String domain, String layer, double strength, String summary) {}
}