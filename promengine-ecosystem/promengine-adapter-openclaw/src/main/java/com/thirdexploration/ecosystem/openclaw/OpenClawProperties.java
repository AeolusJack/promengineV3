package com.thirdexploration.ecosystem.openclaw;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.ecosystem.openclaw")
public class OpenClawProperties {
    private boolean enabled = false;
    private String skillsPath = "./skills";
    private String sandbox = "docker";
}