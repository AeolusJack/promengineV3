package com.thirdexploration.promengine.memory.model;

import lombok.Builder;
import lombok.Data;

/**
 * 记忆之间的因果关联。
 * 用于支持因果推理和溯源查询。
 */
@Data
@Builder
public class CausalLink {

    /**
     * 关联唯一标识
     */
    private String id;

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
     * 关联置信度 (0-1)
     */
    private float confidence;

    /**
     * 创建时间
     */
    private java.time.Instant createdAt;

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
     * 创建一条因果关联
     */
    public static CausalLink create(String sourceId, String targetId, RelationType type, float confidence) {
        return CausalLink.builder()
                .id("link_" + java.util.UUID.randomUUID().toString().replace("-", ""))
                .sourceMemoryId(sourceId)
                .targetMemoryId(targetId)
                .relationType(type)
                .confidence(confidence)
                .createdAt(java.time.Instant.now())
                .build();
    }
}