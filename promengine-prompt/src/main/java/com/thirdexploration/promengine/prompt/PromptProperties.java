package com.thirdexploration.promengine.prompt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.prompt")
public class PromptProperties {
    private String templatesPath = "./configs/prompts/";
    private String defaultTemplate = "default";
    private CompressionConfig compression = new CompressionConfig();

    @Data
    public static class CompressionConfig {
        private boolean enabled = true;
        private int targetMaxTokens = 7000;
        private double forceChunkThreshold = 0.8;
        private double chunkOverlapRatio = 0.1;
    }
}