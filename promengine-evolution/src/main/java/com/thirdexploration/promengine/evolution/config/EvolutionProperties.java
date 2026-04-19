package com.thirdexploration.promengine.evolution.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.evolution")
public class EvolutionProperties {
    private boolean regretEnabled = false;
    private boolean reflectionEnabled = true;
    private boolean adversarialEnabled = false;
}