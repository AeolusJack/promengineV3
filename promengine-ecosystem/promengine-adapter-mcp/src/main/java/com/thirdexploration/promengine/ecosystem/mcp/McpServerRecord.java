package com.thirdexploration.promengine.ecosystem.mcp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpServerRecord {
    private String id;
    private String name;
    private String url;
    private boolean enabled;
    private long createdAt;
}