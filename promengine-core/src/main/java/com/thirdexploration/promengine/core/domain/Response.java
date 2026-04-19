package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

/**
 * Agent 响应。
 */
@Data
@Builder
public class Response {
    private String text;
    private long processingTimeMs;
    private String modelUsed;
    private double cost;
}