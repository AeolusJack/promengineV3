package com.thirdexploration.promengine.memory.agent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentToolBinding {
    private String id;
    private String tenantId;
    private String agentId;
    private String toolName;
    private String config;     // JSON
    private boolean enabled;
    private long createdAt;
}