package com.thirdexploration.promengine.swarm;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MicroAgentResult {
    private String agentId;
    private String output;
    private float confidence;
    private boolean success;
    private String errorMessage;

    public static MicroAgentResult success(String id, String output, float confidence) {
        return MicroAgentResult.builder()
                .agentId(id)
                .output(output)
                .confidence(confidence)
                .success(true)
                .build();
    }

    public static MicroAgentResult failure(String id, String error) {
        return MicroAgentResult.builder()
                .agentId(id)
                .success(false)
                .errorMessage(error)
                .build();
    }
}