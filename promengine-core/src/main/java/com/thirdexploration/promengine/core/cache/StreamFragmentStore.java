package com.thirdexploration.promengine.core.cache;

import com.thirdexploration.promengine.core.cache.CacheRegion;
import com.thirdexploration.promengine.core.cache.CacheTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamFragmentStore {

    private final CacheTemplate cacheTemplate;
    private final ConcurrentHashMap<String, AtomicBoolean> completedFlags = new ConcurrentHashMap<>();

    public void addFragment(String executionId, String delta) {
        if (executionId == null || delta == null || delta.isEmpty()) return;
        String key = executionId;
        // 获取现有片段列表
        List<String> fragments = cacheTemplate.get(CacheRegion.STREAM_FRAGMENT, key, List.class)
                .orElse(new ArrayList<>());
        // 创建新列表（拷贝），避免并发修改
        List<String> newFragments = new ArrayList<>(fragments);
        newFragments.add(delta);
        cacheTemplate.put(CacheRegion.STREAM_FRAGMENT, key, newFragments);
        log.trace("Fragment stored for {}: {} (total {})", executionId, delta, newFragments.size());
    }

    public List<String> getFragments(String executionId) {
        if (executionId == null) return List.of();
        return cacheTemplate.get(CacheRegion.STREAM_FRAGMENT, executionId, List.class)
                .orElse(List.of());
    }

    public String getAssembledContent(String executionId) {
        return String.join("", getFragments(executionId));
    }

    public void markCompleted(String executionId) {
        if (executionId == null) return;
        completedFlags.computeIfAbsent(executionId, k -> new AtomicBoolean(false)).set(true);
    }

    public boolean isCompleted(String executionId) {
        AtomicBoolean flag = completedFlags.get(executionId);
        return flag != null && flag.get();
    }

    public void evict(String executionId) {
        if (executionId == null) return;
        cacheTemplate.evict(CacheRegion.STREAM_FRAGMENT, executionId);
        completedFlags.remove(executionId);
        log.debug("Evicted stream fragments for {}", executionId);
    }
}