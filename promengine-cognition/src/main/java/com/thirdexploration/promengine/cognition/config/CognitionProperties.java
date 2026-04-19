package com.thirdexploration.promengine.cognition.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.cognition")
public class CognitionProperties {

    private String mode = "silicon"; // silicon / carbon

    private CarbonFuelConfig carbonFuel = new CarbonFuelConfig();

    @Data
    public static class CarbonFuelConfig {
        private boolean enabled = true;
        private int maxFuel = 100;
        private int recoveryPerHour = 10;
        private int boostLimitPerDay = 3;
        private int boostAmount = 30;
    }
}