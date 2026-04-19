package com.thirdexploration.promengine.memory.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * 内部记忆记录，用于在各存储层之间传递。
 */
@Data
@Builder
public class MemoryRecord {
    private String id;
    private String userId;
    private String content;
    private String summary;          // 摘要字段（用于温数据摘要）
    private Instant timestamp;
    private String memoryType;
    private float importance;
    private Map<String, Object> metadata;
    private float[] vector;          // 向量嵌入（由外部生成）
    private Long ttlSeconds;
    private boolean deleted;         // 软删除标记
}