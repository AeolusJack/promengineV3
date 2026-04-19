package com.thirdexploration.ecosystem.browseruse;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.ecosystem.browser-use")
public class BrowserUseProperties {
    private boolean enabled = false;
    private String mode = "rest-bridge";
}