package com.thirdexploration.promengine.memory.agent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentWorkflow {
    private String id;
    private String tenantId;
    private String name;
    private String description;
    private String version;
    private String steps;          // JSON 数组
    private String triggers;       // JSON
    private int maxSteps;
    private int timeoutSeconds;
    private String fallbackStrategy;
    private String createdBy;
    private long createdAt;
    private long updatedAt;
}