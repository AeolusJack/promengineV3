package com.thirdexploration.ecosystem.litellm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.ecosystem.litellm")
public class LiteLLMProperties {
    private String endpoint = "http://localhost:4000";
    private String apiKey = "";
}