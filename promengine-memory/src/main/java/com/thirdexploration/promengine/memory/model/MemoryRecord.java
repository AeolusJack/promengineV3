package com.thirdexploration.promengine.memory.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.*;

/**
 * aeon
 * 完整的记忆记录，贯穿所有存储层级。
 * 注意：domain、layer、sharingLevel 均使用 String 类型，由元数据注册表动态校验。
 */
@Data
@Builder
public class MemoryRecord {

    // ========== 基础标识 ==========
    /**
     * 记忆唯一标识
     */
    private String id;

    /**
     * 所属用户 ID
     */
    private String userId;

    /**
     * 所属项目 ID（可选，用于项目隔离）
     */
    private String projectId;

    /**
     * 对话sessionId
     */
    private String sessionId;

    // ========== 内容 ==========
    /**
     * 完整内容
     */
    private String content;

    /**
     * 内容摘要（用于快速预览和温存储索引）
     */
    private String summary;

    /**
     * 向量表示（用于语义检索）
     */
    private float[] vector;

    // ========== 时间 ==========
    /**
     * 创建时间
     */
    private Instant timestamp;

    /**
     * 最后访问时间
     */
    private Instant lastAccessed;

    /**
     * 生存时间（秒），过期后自动删除
     */
    private Long ttlSeconds;

    // ========== 分类与评分 ==========
    /**
     * 记忆类型（保留原有字段，兼容旧系统）
     */
    private String memoryType;

    /**
     * 记忆层级：working, episodic, semantic, procedural, collective
     */
    private String layer;

    /**
     * 记忆域：general, code, legal 等（由配置定义）
     */
    private String domain;

    /**
     * 重要性 (0-1)
     */
    private float importance;

    /**
     * 遗忘曲线强度 (0-1)，随时间衰减
     */
    private double strength;

    /**
     * 效用评分 (0-1)，TAME 执行者轨道
     */
    private double utilityScore;

    /**
     * 安全评分 (0-1)，TAME 评估者轨道
     */
    private double safetyScore;

    // ========== 组织与标签 ==========
    /**
     * 灵活标签列表
     */
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /**
     * 共享级别：private, domain, global（由配置定义）
     */
    private String sharingLevel;

    // ========== 元数据 ==========
    /**
     * 扩展元数据（JSON 格式存储）
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // ========== 来源 ==========
    /**
     * 来源追踪信息
     */
    private Provenance provenance;

    // ========== 关联 ==========
    /**
     * 因果关联列表
     */
    @Builder.Default
    private List<CausalLink> causalLinks = new ArrayList<>();

    // ========== 状态 ==========
    /**
     * 是否已软删除
     */
    private boolean deleted;

    /**
     * 被检索次数
     */
    private int retrievalCount;

    // ========== 便捷方法 ==========

    /**
     * 增加检索计数并更新最后访问时间
     */
    public void incrementRetrieval() {
        this.retrievalCount++;
        this.lastAccessed = Instant.now();
    }

    /**
     * 判断记忆是否已被验证
     */
    public boolean isVerified() {
        return provenance != null && provenance.isVerified();
    }

    /**
     * 计算当前记忆强度（基于遗忘曲线）
     * @param decayRate 衰减率
     * @return 衰减后的强度
     */
    public float computeDecayedStrength(double decayRate) {
        if (lastAccessed == null) {
            lastAccessed = timestamp;
        }
        long daysSinceLastAccess = (System.currentTimeMillis() - lastAccessed.toEpochMilli()) / (24 * 3600 * 1000);
        return (float) (strength * Math.exp(-decayRate * daysSinceLastAccess));
    }

    /**
     * 生成内容哈希（用于去重）
     */
    public String computeContentHash() {
        return org.springframework.util.DigestUtils.md5DigestAsHex(
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 转换为 MemoryEntry（兼容旧接口）
     */
    // MemoryRecord.java 中的 toMemoryEntry 方法
    public MemoryEntry toMemoryEntry() {
        return MemoryEntry.builder()
                .id(id)
                .userId(userId)
                .content(content)
                .summary(summary)
                .timestamp(timestamp)
                .memoryType(memoryType)
                .importance(importance)
                .metadata(metadata)
                .strength(strength)              // 补全
                .layer(layer)
                .domain(domain)
                .utilityScore(utilityScore)      // 补全
                .safetyScore(safetyScore)        // 补全
                .sharingLevel(sharingLevel)      // 如果前端需要
                .build();
    }
    // 在 MemoryRecord.java 中添加方法
    public Map<String, Object> toMemoryEntryAsMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", this.id);
        map.put("content", this.content);
        map.put("summary", this.summary);
        map.put("domain", this.domain);
        map.put("layer", this.layer);
        map.put("strength", this.strength);
        map.put("importance", this.importance);
        map.put("utilityScore", this.utilityScore);
        map.put("safetyScore", this.safetyScore);
        map.put("timestamp", this.timestamp != null ? this.timestamp.toEpochMilli() : null);
        map.put("memoryType", this.memoryType);
        return map;
    }

}