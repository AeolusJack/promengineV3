package com.thirdexploration.ecosystem.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.ecosystem.mcp")
public class MCPProperties {
    private boolean enabled = true;
    private List<String> servers = List.of();
}