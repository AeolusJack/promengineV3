package com.thirdexploration.promengine.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.models")
public class ModelGatewayProperties {

    private List<ProviderConfig> providers = List.of();
    private RoutingConfig routing = new RoutingConfig();
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
    private String offlineFallback = "local-onnx:tinyllama";

    @Data
    public static class ProviderConfig {
        private String id;
        private String type; // openai-compatible, ollama, litellm
        private String endpoint;
        private String apiKey;
        private List<ModelConfig> models = List.of();

        @Data
        public static class ModelConfig {
            private String name;
            private double costPer1kTokens;
            private long maxContextLength = 8192;
            private Map<String, Object> extraParams;
        }
    }

    @Data
    public static class RoutingConfig {
        private String strategy = "semantic-budget-aware";
        private String complexityModel = "local-onnx:tiny-bert";
        private boolean loadAware = true;
        private int localModelMaxQueue = 3;
        private double loadPenaltyWeight = 0.5;
        private List<String> fallbackChain = List.of("siliconflow", "aliyun-bailian", "local-ollama");
    }

    @Data
    public static class CircuitBreakerConfig {
        private int failureThreshold = 5;
        private int timeout = 30; // seconds
        private int halfOpenMaxRequests = 3;
    }
}