package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 流式响应块。
 */
@Data
@Builder
public class CompletionChunk {
    private String delta;
    private boolean last;
}