package com.thirdexploration.promengine.memory.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * aeon
 * 精简版记忆条目，用于与旧系统兼容及 API 返回。
 */
@Data
@Builder
public class MemoryEntry {

    private String id;
    private String userId;
    private String content;
    private String summary;
    private Instant timestamp;
    private String memoryType;
    private float importance;
    private Map<String, Object> metadata;
    private double strength;
    private String layer;
    private String domain;
    private double utilityScore;
    private double  safetyScore;
    private String sharingLevel;   // 确保存在
}