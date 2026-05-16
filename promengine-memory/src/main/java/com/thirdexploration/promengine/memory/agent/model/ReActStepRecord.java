package com.thirdexploration.promengine.memory.agent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReActStepRecord {
    private String id;
    private String agentId;
    private String sessionId;
    private int stepNumber;
    private String type;         // THINKING, TOOL_CALL, TOOL_RESULT, ERROR, COMPLETE
    private String description;
    private String detail;
    private String status;       // RUNNING, SUCCESS, FAILED
    private long timestamp;
    private String executionId;


}