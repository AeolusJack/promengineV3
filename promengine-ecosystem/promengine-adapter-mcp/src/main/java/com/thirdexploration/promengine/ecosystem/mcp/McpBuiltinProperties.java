package com.thirdexploration.promengine.ecosystem.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.mcp")
public class McpBuiltinProperties {
    private List<BuiltinServerConfig> builtinServers = new ArrayList<>();

    @Data
    public static class BuiltinServerConfig {
        private String id;
        private String name;
        private String url;
        private boolean enabled = true;
        private String authToken;
        private String transport = "sse";
        private String command;
        private String args;
        private Map<String, String> headers = Map.of();
    }
}