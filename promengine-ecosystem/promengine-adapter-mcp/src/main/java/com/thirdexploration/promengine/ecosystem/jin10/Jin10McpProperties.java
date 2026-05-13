package com.thirdexploration.promengine.ecosystem.jin10;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.ecosystem.jin10")
public class Jin10McpProperties {
    /** MCP 服务器 URL */
    private String serverUrl = "https://mcp.jin10.com/mcp";
    /** Bearer Token */
    private String apiKey = "sk-XKP95GTkdSxeiMzfTwYzKyiRzechIbi5lAzpsJKOqs0";
    /** 是否启用 */
    private boolean enabled = true;
    /** 请求超时（秒） */
    private int timeout = 30;
    /** 最大重试次数 */
    private int maxRetries = 2;
    /** MCP 协议版本 */
    private String protocolVersion = "2025-11-25";
}