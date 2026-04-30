package com.thirdexploration.promengine.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/**
 * 缓存操作模板，提供统一的 get/put/evict 方法。
 */
@Component
@RequiredArgsConstructor
public class CacheTemplate {

    private final CacheManager cacheManager;

    /**
     * 从指定区域获取缓存值。
     */
    public Optional<Object> get(CacheRegion region, String key) {
        return Optional.ofNullable(cacheManager.getCache(region).getIfPresent(key));
    }

    /**
     * 从指定区域获取缓存值，并自动类型转换。
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(CacheRegion region, String key, Class<T> type) {
        Object value = cacheManager.getCache(region).getIfPresent(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    /**
     * 放入缓存（使用区域默认 TTL）。
     */
    public void put(CacheRegion region, String key, Object value) {
        cacheManager.getCache(region).put(key, value);
    }

    /**
     * 删除缓存。
     */
    public void evict(CacheRegion region, String key) {
        cacheManager.getCache(region).invalidate(key);
    }

    /**
     * 如果不存在则加载（Read-Through 模式）。
     */
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(CacheRegion region, String key, Function<String, T> loader) {
        return (T) cacheManager.getCache(region).get(key, k -> loader.apply(k));
    }

    /**
     * 原子性更新某个 key 的值。
     */
    public void update(CacheRegion region, String key, Function<Object, Object> updater) {
        cacheManager.getCache(region).asMap().compute(key, (k, v) -> updater.apply(v));
    }
}