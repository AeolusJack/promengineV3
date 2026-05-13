package com.thirdexploration.promengine.ecosystem.mcp;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class McpServerRecord {
    private String id;
    private String name;
    private String url;                    // 服务端地址（sse 模式必填）
    private boolean enabled;
    private long createdAt;
    private String authToken;              // Bearer Token（可选）
    private String transport;             // "sse" 或 "stdio"，默认 "sse"
    private String command;               // stdio 命令（如 "python", "node"）
    private String args;                  // stdio 参数（逗号分隔）
    private Map<String, String> headers;  // 自定义 HTTP 请求头（JSON 存储）
}