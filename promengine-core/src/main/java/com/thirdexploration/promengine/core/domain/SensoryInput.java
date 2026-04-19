package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 感官输入（传递给认知层）。
 */
@Data
@Builder
public class SensoryInput {
    private String userId;
    private String inputText;
    private long typingDurationMs;
    private float sentimentScore;  // -1 到 1
}