package com.thirdexploration.promengine.memory.agent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentExecutionLog {
    private String id;
    private String tenantId;
    private String agentId;
    private String sessionId;
    private String taskId;
    private String stepName;
    private String status;      // running / success / failed / skipped
    private String input;       // JSON
    private String output;      // JSON
    private String errorMessage;
    private long startTime;
    private Long endTime;
    private Long durationMs;
    private long createdAt;
}