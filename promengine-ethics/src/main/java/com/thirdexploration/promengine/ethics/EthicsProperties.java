package com.thirdexploration.promengine.ethics;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.ethics")
public class EthicsProperties {
    private boolean enabled = true;
    private boolean auditEnabled = true;
    private String auditPath = "./data/audit";
}