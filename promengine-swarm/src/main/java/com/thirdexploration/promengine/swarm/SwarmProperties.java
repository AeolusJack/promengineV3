package com.thirdexploration.promengine.swarm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.swarm")
public class SwarmProperties {
    private boolean enabled = true;
    private int maxMicroAgents = 50;
    private int maxConcurrentAgents = 20;
    private Duration agentTimeout = Duration.ofSeconds(30);
    private int globalModelRateLimit = 20;
    private boolean enforceStructuredOutput = true;
}