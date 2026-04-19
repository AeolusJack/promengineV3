package com.thirdexploration.promengine.evolution;

import com.thirdexploration.promengine.core.domain.TaskContext;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegretVector {
    private String sessionId;
    private TaskContext context;
    private String chosenOption;
    private double regretScore;

    public static RegretVector empty() {
        return RegretVector.builder().regretScore(0.0).build();
    }
}