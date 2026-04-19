package com.thirdexploration.promengine.skill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.skill")
public class SkillProperties {
    private String directory = "./skills";
    private boolean hotReload = true;
}