package com.thirdexploration.promengine.memory.retrieval;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    private final Cache<String, float[]> embeddingCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .recordStats()
            .build();

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        String key = DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
        float[] cached = embeddingCache.getIfPresent(key);
        if (cached != null) {
            log.debug("Embedding cache hit for key: {}", key);
            return cached;
        }

        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), null));
        float[] doubleVector = response.getResult().getOutput();
//        double[] doubleVector =
        float[] floatVector = new float[doubleVector.length];
        for (int i = 0; i < doubleVector.length; i++) {
            floatVector[i] = (float) doubleVector[i];
        }
        embeddingCache.put(key, floatVector);
        log.debug("Embedding computed and cached for key: {}", key);
        return floatVector;
    }

    public double getCacheHitRate() {
        return embeddingCache.stats().hitRate();
    }
}