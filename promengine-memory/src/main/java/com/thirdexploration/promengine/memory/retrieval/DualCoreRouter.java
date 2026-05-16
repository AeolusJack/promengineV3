package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.memory.config.AeonMemoryProperties;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 双核路由器：根据查询复杂度和层级选择合适的检索管道。
 * 优化：增加向量相似度复杂度判断，阈值可配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DualCoreRouter {

    private final FastPipeRetriever fastPipe;
    private final DeepPipeRetriever deepPipe;
    private final EmbeddingService embeddingService;
    private final AeonMemoryProperties properties;

    public List<MemoryRecord> route(String layer, MemoryQuery query, List<String> domains) {
        double complexity = estimateComplexity(query);
        log.debug("Routing layer={}, complexity={}", layer, complexity);

        if ("working".equals(layer)) {
            return fastPipe.retrieveWorking(query);
        }

        double lowThreshold = properties.getComplexityLowThreshold();   // 默认0.3
        double highThreshold = properties.getComplexityHighThreshold(); // 默认0.7

        if (complexity < lowThreshold) {
            return fastPipe.retrieve(layer, query, domains);
        } else if (complexity < highThreshold) {
            return deepPipe.retrieve(layer, query, domains, "light-llm");
        } else {
            return deepPipe.retrieve(layer, query, domains, "deep-llm");
        }
    }

    private double estimateComplexity(MemoryQuery query) {
        String text = query.getText();
        if (text == null || text.isBlank()) return 0.1;

        double score = Math.min(text.length() / 200.0, 0.5);

        String lower = text.toLowerCase();
        if (lower.contains("为什么") || lower.contains("原因")) score += 0.3;
        if (lower.contains("如何") || lower.contains("怎么")) score += 0.2;
        if (query.isCrossDomain()) score += 0.2;

        // 短文本但语义复杂：通过向量与简单句的相似度判断
        if (text.length() < 20 && embeddingService != null) {
            try {
                float[] vec = embeddingService.embed(text);
                if (vec != null && vec.length > 0) {
                    // 假设简单句向量是预设的（这里用查询“你好”的向量作为基准）
                    float[] simpleVec = embeddingService.embed("你好");
                    double similarity = cosineSimilarity(vec, simpleVec);
                    if (similarity < 0.3) {
                        score += 0.2; // 与简单句差异大，提高复杂度
                    }
                }
            } catch (Exception e) {
                log.debug("Vector complexity estimation failed", e);
            }
        }

        return Math.min(score, 1.0);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return 0;
        double dot = 0, normA = 0, normB = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}