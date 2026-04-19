package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * 记忆检索策略。
 */
@Data
@Builder
public class RetrievalStrategy {
    private Duration timeWindow;          // 默认 30 天
    private boolean allowColdStorageScan;
    private int topK;
}