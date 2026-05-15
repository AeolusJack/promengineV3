package com.thirdexploration.promengine.memory.agent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentHumanReview {
    private String id;
    private String agentId;
    private String sessionId;
    private String taskId;
    private String requestType;  // confirmation / input / choice
    private String requestData;  // JSON
    private String responseData; // JSON
    private String status;       // pending / approved / rejected / timeout
    private long createdAt;
    private Long resolvedAt;
}