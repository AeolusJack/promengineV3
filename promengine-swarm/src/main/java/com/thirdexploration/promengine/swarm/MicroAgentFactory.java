package com.thirdexploration.promengine.swarm;

import com.thirdexploration.promengine.core.util.IdGenerator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MicroAgentFactory {

    public MicroAgent createForSubtask(String subtask) {
        return MicroAgent.builder()
                .id(IdGenerator.generateWithPrefix("swarm"))
                .capability("general")
                .promptTemplate("完成以下子任务：{subtask}")
                .config(Map.of("subtask", subtask))
                .build();
    }
}