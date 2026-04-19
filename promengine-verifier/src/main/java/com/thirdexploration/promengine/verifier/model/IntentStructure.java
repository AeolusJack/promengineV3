package com.thirdexploration.promengine.verifier.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class IntentStructure {
    private String action;
    private String target;
    private Map<String, Object> params;
    private String delegationLevel;

    public String toJson() {
        // 简化为字符串
        return String.format("{\"action\":\"%s\",\"target\":\"%s\"}", action, target);
    }
}