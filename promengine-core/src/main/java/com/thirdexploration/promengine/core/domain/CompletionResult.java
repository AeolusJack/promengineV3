package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 模型完成结果。
 */
@Data
@Builder
public class CompletionResult {
    private String content;
    private String finishReason;
    private long promptTokens;
    private long completionTokens;
    private long latencyMs;
}