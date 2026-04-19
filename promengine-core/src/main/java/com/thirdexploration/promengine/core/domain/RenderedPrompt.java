package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 渲染后的提示词。
 */
@Data
@Builder
public class RenderedPrompt {
    private String templateId;
    private String version;
    private String finalPrompt;
    private int tokenCount;
}