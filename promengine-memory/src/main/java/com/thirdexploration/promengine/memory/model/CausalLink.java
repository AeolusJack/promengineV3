package com.thirdexploration.promengine.memory.model;

import lombok.Builder;
import lombok.Data;
import lombok.Builder.Default;

import java.time.Instant;
import java.util.UUID;

/**
 * 记忆之间的因果关联。
 * 用于支持因果推理、图谱扩展和溯源查询。
 * 优化：增加 weight 权重字段、自动生成 ID 和创建时间、提供便捷构建方法。
 */
@Data
@Builder
public class CausalLink {

    /**
     * 关联唯一标识（自动生成）
     */
    @Default
    private String id = generateId();

    /**
     * 源记忆 ID
     */
    private String sourceMemoryId;

    /**
     * 目标记忆 ID
     */
    private String targetMemoryId;

    /**
     * 关系类型
     */
    private RelationType relationType;

    /**
     * 关联强度/权重（0-1），用于图谱融合排序
     */
    @Default
    private double weight = 1.0;

    /**
     * 关联置信度 (0-1)
     */
    @Default
    private float confidence = 1.0f;

    /**
     * 可选的描述信息
     */
    private String description;

    /**
     * 创建时间（自动填充）
     */
    @Default
    private Instant createdAt = Instant.now();

    /**
     * 关系类型枚举
     */
    public enum RelationType {
        CAUSED_BY,      // 由...导致
        PRECEDES,       // 先于...发生
        CONTRADICTS,    // 与...矛盾
        SUPPORTS,       // 支持...的结论
        IMPLEMENTS,     // 实现了...
        DERIVED_FROM    // 派生自...
    }

    /**
     * 创建一条因果关联（推荐使用此工厂方法）
     * @param sourceId 源记忆 ID
     * @param targetId 目标记忆 ID
     * @param type 关系类型
     * @param weight 权重（0-1）
     * @param confidence 置信度（0-1）
     * @return 构建完成的 CausalLink 实例
     */
    public static CausalLink create(String sourceId, String targetId, RelationType type, double weight, float confidence) {
        return CausalLink.builder()
                .sourceMemoryId(sourceId)
                .targetMemoryId(targetId)
                .relationType(type)
                .weight(weight)
                .confidence(confidence)
                .build();
    }

    /**
     * 简化版创建（使用默认权重和置信度）
     */
    public static CausalLink create(String sourceId, String targetId, RelationType type) {
        return create(sourceId, targetId, type, 1.0, 1.0f);
    }

    /**
     * 生成唯一 ID
     */
    private static String generateId() {
        return "link_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 便捷方法：判断是否为主要因果（权重高于阈值）
     */
    public boolean isMajor() {
        return weight >= 0.7;
    }

    /**
     * 反转链接（交换 source 和 target）
     */
    public CausalLink reverse() {
        RelationType reversedType = switch (relationType) {
            case CAUSED_BY -> RelationType.CAUSED_BY; // 实际应映射为 "CAUSES"，但无对应枚举，保持原样或需扩展
            case PRECEDES -> RelationType.PRECEDES;
            case CONTRADICTS -> RelationType.CONTRADICTS;
            case SUPPORTS -> RelationType.SUPPORTS;
            case IMPLEMENTS -> RelationType.IMPLEMENTS;
            case DERIVED_FROM -> RelationType.DERIVED_FROM;
        };
        return CausalLink.builder()
                .sourceMemoryId(targetMemoryId)
                .targetMemoryId(sourceMemoryId)
                .relationType(reversedType)
                .weight(weight)
                .confidence(confidence)
                .description(description)
                .build();
    }
}