package com.thirdexploration.promengine.memory.agent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentTemplate {
    private String id;
    private String tenantId;
    private String name;
    private String category;
    private String description;
    private String templateConfig; // JSON
    private String createdBy;
    private String visibility;
    private int downloads;
    private long createdAt;
    private long updatedAt;
}