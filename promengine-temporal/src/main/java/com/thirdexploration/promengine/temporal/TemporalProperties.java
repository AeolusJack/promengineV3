package com.thirdexploration.promengine.temporal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.temporal")
public class TemporalProperties {
    private boolean enabled = true;
    private int densityWindowSeconds = 300;
}