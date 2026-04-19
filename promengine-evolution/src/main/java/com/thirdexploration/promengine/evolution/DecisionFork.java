package com.thirdexploration.promengine.evolution;

import com.thirdexploration.promengine.core.domain.TaskContext;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DecisionFork {
    private String sessionId;
    private TaskContext context;
    private List<String> options;
    private long timestamp;
}