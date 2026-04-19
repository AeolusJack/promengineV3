package com.thirdexploration.promengine.devtools;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.devtools")
public class DevToolsProperties {
    private boolean debugTraceEnabled = false;
    private boolean shadowModeEnabled = false;
    private boolean hotReload = true;
}