package com.thirdexploration.promengine.runtime.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentRecord {
    private String id;
    private String userId;
    private String name;
    private String description;
    private String avatar;
    private String mode;               // silicon / carbon
    private boolean isIndependent;
    private String systemPrompt;
    private String skills;             // JSON array
    private String tools;              // JSON array
    private String proactiveLevel;     // none / query / remind
    private String schedule;           // cron expression
    private String modelPreference;
    private String memoryDomain;
    private String visibility;         // private / public
    private boolean enabled;
    private long createdAt;
    private String createdBy;

}