package com.thirdexploration.promengine.memory.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * aeon
 * 工作记忆管理器（L1）。
 * 基于 Caffeine 缓存实现，容量和 TTL 由元数据注册表中的 working 层级配置决定。
 * 记忆以 sessionId 为维度组织，任务结束时自动提升至情景记忆。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkingMemoryManager {

    private final MemoryMetadataRegistry registry;

    /**
     * 工作记忆缓存，key 格式: sessionId:memoryId
     */
    private Cache<String, MemoryRecord> workingCache;

    /**
     * 会话活跃状态跟踪
     */
    private final Map<String, Boolean> activeSessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Duration ttl = registry.getLayerTTL("working");
        int capacity = registry.getLayerMaxCapacity("working");
        if (ttl == null) {
            ttl = Duration.ofMinutes(30);
        }
        workingCache = Caffeine.newBuilder()
                .maximumSize(capacity)
                .expireAfterWrite(ttl)
                .build();
        log.info("WorkingMemoryManager initialized: capacity={}, ttl={}", capacity, ttl);
    }

    /**
     * 存入一条工作记忆。
     * @param sessionId 会话 ID
     * @param record 记忆记录
     */
    public void put(String sessionId, MemoryRecord record) {
        if (record.getId() == null) {
            record.setId(generateId());
        }
        record.setLayer("working");
        String key = sessionId + ":" + record.getId();
        workingCache.put(key, record);
        activeSessions.putIfAbsent(sessionId, true);
        log.debug("Working memory stored: session={}, id={}", sessionId, record.getId());
    }

    /**
     * 获取指定会话的所有工作记忆。
     * @param sessionId 会话 ID
     * @return 记忆列表
     */
    public List<MemoryRecord> getBySession(String sessionId) {
        return workingCache.asMap().entrySet().stream()
                .filter(e -> e.getKey().startsWith(sessionId + ":"))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * 获取指定会话中符合查询条件的记忆。
     * @param sessionId 会话 ID
     * @param text 查询文本（简单关键词匹配）
     * @param limit 最大返回数
     * @return 记忆列表
     */
    public List<MemoryRecord> queryBySession(String sessionId, String text, int limit) {
        String lowerText = text != null ? text.toLowerCase() : "";
        return workingCache.asMap().entrySet().stream()
                .filter(e -> e.getKey().startsWith(sessionId + ":"))
                .map(Map.Entry::getValue)
                .filter(r -> lowerText.isEmpty() || r.getContent().toLowerCase().contains(lowerText))
                .limit(limit)
                .toList();
    }

    /**
     * 清空指定会话的所有工作记忆（通常由任务结束触发）。
     * @param sessionId 会话 ID
     * @return 被清空的记忆列表（用于提升至情景记忆）
     */
    public List<MemoryRecord> clearSession(String sessionId) {
        List<MemoryRecord> records = getBySession(sessionId);
        workingCache.asMap().keySet().removeIf(key -> key.startsWith(sessionId + ":"));
        activeSessions.remove(sessionId);
        log.debug("Cleared working memory for session: {}, count={}", sessionId, records.size());
        return records;
    }

    /**
     * 检查会话是否活跃。
     * @param sessionId 会话 ID
     * @return 是否活跃
     */
    public boolean isSessionActive(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }

    /**
     * 获取当前工作记忆总数。
     * @return 记录数量
     */
    public long size() {
        workingCache.cleanUp();
        return workingCache.estimatedSize();
    }

    private String generateId() {
        return "wm_" + UUID.randomUUID().toString().replace("-", "");
    }
}