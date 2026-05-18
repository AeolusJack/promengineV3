package com.thirdexploration.promengine.runtime.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.devtools")
public class ShadowModeProperties {
    private boolean shadowModeEnabled = false;
}