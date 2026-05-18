package com.thirdexploration.promengine.memory.agent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentEvaluation {
    private String id;
    private String tenantId;
    private String agentId;
    private String sessionId;
    private int rating;         // 1~5
    private String tags;        // JSON 数组
    private String comment;
    private long createdAt;
}