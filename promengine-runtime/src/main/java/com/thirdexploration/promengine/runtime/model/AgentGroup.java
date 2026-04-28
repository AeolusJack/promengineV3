package com.thirdexploration.promengine.runtime.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AgentGroup {
    private String id;
    private String userId;
    private String name;
    private String topic;
    private int maxRounds;
    private boolean autoMode;
    private String status;
    private long createdAt;
    private List<GroupAgent> agents;
}

