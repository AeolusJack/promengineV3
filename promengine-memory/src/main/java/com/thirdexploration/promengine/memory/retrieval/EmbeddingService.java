package com.thirdexploration.promengine.memory.retrieval;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final Cache<String, float[]> cache;

    // 根据模型文档设置最大 token 数，保守估计 500 token ≈ 2000 字符
    private static final int MAX_CHARS_PER_CHUNK = 2000;
    private static final int CHUNK_OVERLAP = 200;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .recordStats()
                .build();
    }

    /**
     * 获取文本的向量表示，自动处理分块与缓存
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        String key = DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
        float[] cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        float[] vector;
        if (text.length() <= MAX_CHARS_PER_CHUNK) {
            vector = embedSingle(text);
        } else {
            vector = embedChunked(text);
        }
        cache.put(key, vector);
        return vector;
    }

    /**
     * 单次嵌入（短文本）
     */
    private float[] embedSingle(String text) {
        try {
            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(List.of(text), null));
            return response.getResult().getOutput(); // Spring AI M7 返回 float[]
        } catch (Exception e) {
            log.error("Embedding failed for short text", e);
            return new float[0];
        }
    }

    /**
     * 分块嵌入并聚合（取平均）
     */
    private float[] embedChunked(String text) {
        List<float[]> chunkVectors = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHARS_PER_CHUNK, text.length());
            String chunk = text.substring(start, end);
            float[] vec = embedSingle(chunk);
            if (vec.length > 0) {
                chunkVectors.add(vec);
            }
            start = end - CHUNK_OVERLAP;
            if (start >= text.length()) break;
        }

        if (chunkVectors.isEmpty()) {
            return new float[0];
        }
        int dim = chunkVectors.get(0).length;
        float[] aggregated = new float[dim];
        for (float[] vec : chunkVectors) {
            for (int i = 0; i < dim; i++) {
                aggregated[i] += vec[i];
            }
        }
        for (int i = 0; i < dim; i++) {
            aggregated[i] /= chunkVectors.size();
        }
        log.debug("Aggregated embedding from {} chunks", chunkVectors.size());
        return aggregated;
    }

    public double getCacheHitRate() {
        return cache.stats().hitRate();
    }
}