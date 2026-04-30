package com.thirdexploration.promengine.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 统一缓存管理器，管理多个命名空间的 Caffeine 缓存实例。
 */
@Slf4j
@Component
public class CacheManager {

    private final Map<String, Cache<String, Object>> caches = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public CacheManager(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 获取指定区域的缓存实例，若不存在则自动创建。
     */
    @SuppressWarnings("unchecked")
    public Cache<String, Object> getCache(CacheRegion region) {
        return caches.computeIfAbsent(region.getRegionName(), key -> buildCache(region));
    }

    /**
     * 自定义区域的缓存（支持动态参数）。
     */
    public Cache<String, Object> getOrCreate(String regionName, int maxSize, int ttlSeconds) {
        return caches.computeIfAbsent(regionName, key -> buildCache(regionName, maxSize, ttlSeconds));
    }

    /**
     * 清除指定区域的所有缓存。
     */
    public void evictRegion(CacheRegion region) {
        Cache<String, Object> cache = caches.remove(region.getRegionName());
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    /**
     * 获取所有缓存统计信息。
     */
    public Map<String, CacheStats> getAllStats() {
        Map<String, CacheStats> stats = new ConcurrentHashMap<>();
        caches.forEach((key, cache) -> stats.put(key, cache.stats()));
        return stats;
    }

    private Cache<String, Object> buildCache(CacheRegion region) {
        return buildCache(region.getRegionName(), region.getMaxSize(), region.getTtlSeconds());
    }

    private Cache<String, Object> buildCache(String name, int maxSize, int ttlSeconds) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .recordStats();
        Cache<String, Object> cache = builder.build();
        // 注册到 Micrometer
        CaffeineCacheMetrics.monitor(meterRegistry, cache, name);
        log.info("Cache region '{}' created: maxSize={}, ttl={}s", name, maxSize, ttlSeconds);
        return cache;
    }
}