package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 模型完成请求。
 */
@Data
@Builder
public class CompletionRequest {
    private String modelId;
    private String prompt;
    private int maxTokens;
    private float temperature;

    private Map<String, Object> additionalParams;
    private TaskContext taskContext;
    // 新增：是否在响应中包含模型的思考过程（thinking）
    @Builder.Default
    private boolean includeThinking = false;
}