package com.thirdexploration.promengine.memory.model;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * aeon
 * 写入记忆时的元数据，用于指导路由、分层和权限设置。
 */
@Data
@Builder
public class MemoryMetadata {

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 目标记忆域
     */
    private String domain;

    /**
     * 项目 ID
     */
    private String projectId;

    /**
     * 建议的记忆层级（系统可根据策略覆盖）
     */
    private String layerHint;

    /**
     * 重要性 (0-1)
     */
    private float importance;

    /**
     * 共享级别
     */
    private String sharingLevel;

    /**
     * 来源类型
     */
    private String source;

    /**
     * 扩展字段
     */
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();

    /**
     * 创建用户输入的元数据
     */
    public static MemoryMetadata fromUserInput(String userId, String sessionId) {
        return MemoryMetadata.builder()
                .userId(userId)
                .sessionId(sessionId)
                .source("user_input")
                .domain("general")
                .sharingLevel("private")
                .importance(0.5f)
                .build();
    }

    /**
     * 创建工具输出的元数据
     */
    public static MemoryMetadata fromToolOutput(String userId, String toolName) {
        return MemoryMetadata.builder()
                .userId(userId)
                .source("tool_output")
                .domain("general")
                .sharingLevel("private")
                .importance(0.7f)
                .extra(Map.of("tool", toolName))
                .build();
    }

    /**
     * 创建 Agent 生成的元数据
     */
    public static MemoryMetadata fromAgentGenerated(String userId, String agentId) {
        return MemoryMetadata.builder()
                .userId(userId)
                .source("agent_generated")
                .domain("general")
                .sharingLevel("private")
                .importance(0.6f)
                .extra(Map.of("agent", agentId))
                .build();
    }
}