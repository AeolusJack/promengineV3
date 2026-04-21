package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

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
}