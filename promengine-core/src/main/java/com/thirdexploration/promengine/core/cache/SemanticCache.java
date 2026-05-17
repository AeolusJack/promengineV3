package com.thirdexploration.promengine.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thirdexploration.promengine.core.domain.CompletionResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class SemanticCache {
    // 缓存 key: 嵌入向量的前8位hex, value: 缓存条目
    private final Cache<String, CachedAnswer> cache;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public SemanticCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .build();
    }

    public CompletionResult get(String prompt, float[] vector) {
        String key = vectorToKey(vector);
        CachedAnswer answer = cache.getIfPresent(key);
        if (answer != null && answer.prompt().equals(prompt)) {
            return answer.result();
        }
        return null;
    }

    public void put(String prompt, float[] vector, CompletionResult result) {
        String key = vectorToKey(vector);
        cache.put(key, new CachedAnswer(prompt, result));
    }

    private String vectorToKey(float[] vector) {
        // 简单哈希作为key
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(8, vector.length); i++) {
            sb.append(Integer.toHexString(Float.floatToIntBits(vector[i])));
        }
        return sb.toString();
    }

    private record CachedAnswer(String prompt, CompletionResult result) {}
}