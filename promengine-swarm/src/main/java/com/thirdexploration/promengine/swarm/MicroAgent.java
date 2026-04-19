package com.thirdexploration.promengine.swarm;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class MicroAgent {
    private String id;
    private String capability; // 能力类型
    private String promptTemplate;
    private Map<String, Object> config;
}