package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 任务上下文（用于提示词渲染和决策）。
 */
@Data
@Builder
public class TaskContext {
    private String taskType;
    private String userId;
    private UserInput userInput;
    private Map<String, Object> variables;
}