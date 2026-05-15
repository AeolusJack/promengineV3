package com.thirdexploration.promengine.core.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TaskPlan {
    private List<Step> steps;

    @Data
    @Builder
    public static class Step {
        private String id;
        private String tool;           // 工具名，如 "write_files"
        private Map<String, Object> args; // 工具参数
        private String description;
        private String parallelGroup;  // 可选，用于并行执行
        private int maxRetries = 3;
        private String fallback;       // 失败后降级的步骤 ID
    }
}