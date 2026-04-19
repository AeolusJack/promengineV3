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
}