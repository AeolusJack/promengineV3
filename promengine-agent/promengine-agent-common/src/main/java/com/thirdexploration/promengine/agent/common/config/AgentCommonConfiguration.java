package com.thirdexploration.promengine.agent.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.agent.common.planning.DelegatingTaskPlanningStrategy;
import com.thirdexploration.promengine.core.agent.TaskPlanningStrategy;
import com.thirdexploration.promengine.skill.SkillRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentCommonConfiguration {

    @Bean
    public TaskPlanningStrategy taskPlanningStrategy(
            ChatClient.Builder chatClientBuilder,
            SkillRegistry skillRegistry,
            ObjectMapper objectMapper) {
        return new DelegatingTaskPlanningStrategy(chatClientBuilder, skillRegistry, objectMapper);
    }
}