package com.thirdexploration.promengine.prompt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.prompt")
public class PromptProperties {
    private String templatesPath = "./configs/prompts/";
    private String defaultTemplate = "default";
    private int defaultTopK = 5;
    
    private CompressionConfig compression = new CompressionConfig();
    private MetaPromptConfig metaPrompt = new MetaPromptConfig();
    private GraphRagConfig graphRag = new GraphRagConfig();

    @Data
    public static class CompressionConfig {
        private boolean enabled = true;
        private String engine = "simple";    // simple, llmlingua2
        private int targetMaxTokens = 7000;
        private double forceChunkThreshold = 0.8;
        private double chunkOverlapRatio = 0.1;
    }

    @Data
    public static class MetaPromptConfig {
        private boolean enabled = false;
        private boolean selfRewritingEnabled = false;
        private String optimizer = "text-grad";
    }

    @Data
    public static class GraphRagConfig {
        private boolean enabled = false;
        private boolean entityExtractionEnabled = false;
    }
}