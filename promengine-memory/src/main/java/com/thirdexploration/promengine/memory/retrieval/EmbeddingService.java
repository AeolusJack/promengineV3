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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final Cache<String, float[]> successCache;
    private final Cache<String, Boolean> failureCache;
    // 并发去重 map
    private final ConcurrentHashMap<String, CompletableFuture<float[]>> pendingRequests = new ConcurrentHashMap<>();

    private static final int MAX_CHARS_PER_CHUNK = 2000;
    private static final int CHUNK_OVERLAP = 200;
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 500;
    private static final long EMBEDDING_TIMEOUT_SECONDS = 10;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.successCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .recordStats()
                .build();
        this.failureCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) return new float[0];

        // 1. 规范化文本（去除首尾空白、合并空白、截断过长文本）
        String normalized = normalizeText(text);
        String key = DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));

        // 2. 成功缓存
        float[] cached = successCache.getIfPresent(key);
        if (cached != null) return cached;

        // 3. 失败缓存
        if (failureCache.getIfPresent(key) != null) {
            log.trace("Skipping embedding for text (cached failure): key={}", key);
            return new float[0];
        }

        // 4. 并发去重：同一 key 只允许一个实际请求
        CompletableFuture<float[]> future = pendingRequests.get(key);
        if (future == null) {
            future = CompletableFuture.supplyAsync(() -> {
                try {
                    float[] vector = tryEmbedWithRetry(normalized);
                    if (vector != null && vector.length > 0) {
                        successCache.put(key, vector);
                        return vector;
                    } else {
                        failureCache.put(key, Boolean.TRUE);
                        return new float[0];
                    }
                } finally {
                    pendingRequests.remove(key);
                }
            });
            pendingRequests.putIfAbsent(key, future);
            future = pendingRequests.get(key);
        }

        try {
            // 等待最多 30 秒，避免永久阻塞
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Embedding failed for key {}: {}", key, e.getMessage());
            failureCache.put(key, Boolean.TRUE);
            return new float[0];
        }
    }

    private String normalizeText(String text) {
        String trimmed = text.trim();
        // 限制最大长度，避免存储过长文本
        if (trimmed.length() > 2000) {
            trimmed = trimmed.substring(0, 2000);
        }
        // 将多个空白字符压缩为一个空格
        trimmed = trimmed.replaceAll("\\s+", " ");
        return trimmed;
    }

    private float[] tryEmbedWithRetry(String text) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<float[]> future = executor.submit(() -> {
                    if (text.length() <= MAX_CHARS_PER_CHUNK) {
                        return embedSingle(text);
                    } else {
                        return embedChunked(text);
                    }
                });
                float[] result = future.get(EMBEDDING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                executor.shutdownNow();
                if (result != null && result.length > 0) {
                    return result;
                }
                lastException = new RuntimeException("Empty embedding result");
            } catch (TimeoutException e) {
                lastException = e;
                log.debug("Embedding attempt {} timed out after {}s", attempt, EMBEDDING_TIMEOUT_SECONDS);
            } catch (Exception e) {
                lastException = e;
                log.debug("Embedding attempt {} failed: {}", attempt, e.getMessage());
            }
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.debug("Embedding failed after {} attempts", MAX_RETRIES);
        return null;
    }

    private float[] embedSingle(String text) {
        EmbeddingResponse response = embeddingModel.call(
                new EmbeddingRequest(List.of(text), null));
        return response.getResult().getOutput();
    }

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
        if (chunkVectors.isEmpty()) return new float[0];
        int dim = chunkVectors.get(0).length;
        float[] aggregated = new float[dim];
        for (float[] vec : chunkVectors) {
            for (int i = 0; i < dim; i++) aggregated[i] += vec[i];
        }
        for (int i = 0; i < dim; i++) aggregated[i] /= chunkVectors.size();
        return aggregated;
    }

    public double getCacheHitRate() {
        return successCache.stats().hitRate();
    }
}