package com.thirdexploration.promengine.neuro;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.neuro")
public class NeuroProperties {
    private boolean thinkingRippleEnabled = true;
    private String rippleSyncMode = "chunk-aligned";
}