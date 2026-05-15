package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 用户输入封装。
 */
@Data
@Builder
public class UserInput {
    private String sessionId;
    private String text;
    private long timestamp;

    // 新增字段
    private String userId;      // 用户标识
    private String domain;      // 当前使用的记忆域，如 "general" 或 "code"

    // 新增：用于传递额外上下文（如 agentId、agentConfig 等）
    @Builder.Default
    private Map<String, Object> metadata = Map.of();
}