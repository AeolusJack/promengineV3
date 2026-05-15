package com.thirdexploration.promengine.memory.agent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentKnowledgeBase {
    private String id;
    private String agentId;
    private String name;
    private String type;       // vector / graph / rule_file / memory_domain
    private String config;     // JSON
    private int priority;
    private boolean enabled;
    private long createdAt;
    private long updatedAt;
}