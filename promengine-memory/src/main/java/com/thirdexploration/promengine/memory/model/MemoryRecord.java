package com.thirdexploration.promengine.memory.model;

import lombok.Builder;
import lombok.Data;
import lombok.Builder.Default;

import java.time.Instant;
import java.util.*;

/**
 * 完整的记忆记录，贯穿所有存储层级。
 * 优化：增加空安全、序列化兼容、toMemoryEntry 字段补全。
 */
@Data
@Builder
public class MemoryRecord {

    // ========== 基础标识 ==========
    private String id;
    private String userId;
    private String projectId;
    private String sessionId;
    private String tenantId;
    // ========== 内容 ==========
    private String content;
    private String summary;
    private float[] vector;

    // ========== 时间 ==========
    private Instant timestamp;
    private Instant lastAccessed;
    private Long ttlSeconds;

    // ========== 分类与评分 ==========
    private String memoryType;
    private String layer;
    private String domain;

    @Default private float importance = 0.5f;
    @Default private double strength = 1.0;
    @Default private double utilityScore = 0.5;
    @Default private double safetyScore = 0.9;

    // ========== 组织与标签 ==========
    @Default private List<String> tags = new ArrayList<>();

    private String sharingLevel;

    // ========== 元数据 ==========
    @Default private Map<String, Object> metadata = new HashMap<>();

    // ========== 来源 ==========
    private Provenance provenance;

    // ========== 关联 ==========
    @Default private List<CausalLink> causalLinks = new ArrayList<>();

    // ========== 状态 ==========
    @Default private boolean deleted = false;
    @Default private int retrievalCount = 0;

    // ========== 便捷方法 ==========

    public void incrementRetrieval() {
        this.retrievalCount++;
        this.lastAccessed = Instant.now();
    }

    public boolean isVerified() {
        return provenance != null && provenance.isVerified();
    }

    public float computeDecayedStrength(double decayRate) {
        Instant last = lastAccessed != null ? lastAccessed : timestamp;
        if (last == null) return (float) strength;
        long daysSinceLastAccess = (System.currentTimeMillis() - last.toEpochMilli()) / (24 * 3600 * 1000);
        return (float) (strength * Math.exp(-decayRate * daysSinceLastAccess));
    }

    public String computeContentHash() {
        if (content == null) return "";
        return org.springframework.util.DigestUtils.md5DigestAsHex(
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 转换为 MemoryEntry（兼容旧接口）
     */
    public MemoryEntry toMemoryEntry() {
        return MemoryEntry.builder()
                .id(id)
                .userId(userId)
                .content(content)
                .summary(summary)
                .timestamp(timestamp)
                .memoryType(memoryType)
                .importance(importance)
                .metadata(metadata != null ? metadata : new HashMap<>())
                .strength(strength)
                .layer(layer)
                .domain(domain)
                .utilityScore(utilityScore)
                .safetyScore(safetyScore)
                .sharingLevel(sharingLevel)
                .build();
    }

    /**
     * 转换为 Map（用于调试或前端展示）
     */
    public Map<String, Object> toMemoryEntryAsMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("content", content);
        map.put("summary", summary);
        map.put("domain", domain);
        map.put("layer", layer);
        map.put("strength", strength);
        map.put("importance", importance);
        map.put("utilityScore", utilityScore);
        map.put("safetyScore", safetyScore);
        map.put("timestamp", timestamp != null ? timestamp.toEpochMilli() : null);
        map.put("memoryType", memoryType);
        map.put("sessionId", sessionId);
        return map;
    }
}