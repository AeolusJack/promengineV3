package com.thirdexploration.promengine.skill.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SkillRecord {
    private String id;
    private String tenantId;
    private String name;
    private String description;
    private String version;
    private String source;
    private String content;
    private boolean enabled;
    private String associatedAgents; // JSON array string
    private String parameters;       // JSON object string
    private long createdAt;
    private long updatedAt;
    private boolean  published;
}