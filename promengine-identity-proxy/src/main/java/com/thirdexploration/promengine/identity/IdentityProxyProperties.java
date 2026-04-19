package com.thirdexploration.promengine.identity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.identity-proxy")
public class IdentityProxyProperties {
    private boolean enabled = false;
}