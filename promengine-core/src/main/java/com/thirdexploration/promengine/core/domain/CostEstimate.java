package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 成本预估。
 */
@Data
@Builder
public class CostEstimate {
    private double estimatedCost;
    private String currency;
    private long estimatedInputTokens;
    private long estimatedOutputTokens;
}