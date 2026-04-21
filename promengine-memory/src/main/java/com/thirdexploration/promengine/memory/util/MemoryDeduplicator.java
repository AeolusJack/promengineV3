package com.thirdexploration.promengine.memory.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * aeon
 * 写入去重
 */
@Component
public class MemoryDeduplicator {

    private final Cache<String, Boolean> recentHashes = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    public boolean isDuplicate(String content) {
        String hash = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
        Boolean exists = recentHashes.getIfPresent(hash);
        if (exists != null) {
            return true;
        }
        recentHashes.put(hash, true);
        return false;
    }
}