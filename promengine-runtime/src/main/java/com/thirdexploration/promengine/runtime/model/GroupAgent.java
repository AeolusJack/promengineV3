package com.thirdexploration.promengine.runtime.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupAgent {
    private String tenantId;
    private String agentId;
    private String name;
    private String avatar;
    private String role;
}