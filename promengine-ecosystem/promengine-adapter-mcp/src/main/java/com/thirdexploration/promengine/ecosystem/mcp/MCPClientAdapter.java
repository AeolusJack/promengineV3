package com.thirdexploration.promengine.ecosystem.mcp;

import com.thirdexploration.promengine.executor.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MCPClientAdapter {

    private final MCPProperties properties;
    private final WebClient webClient = WebClient.create();

    public List<Tool> discoverTools() {
        // 通过 MCP 协议发现工具
        return properties.getServers().stream()
                .flatMap(server -> fetchToolsFromServer(server).stream())
                .toList();
    }

    private List<Tool> fetchToolsFromServer(String serverUrl) {
        try {
            Map response = webClient.get()
                    .uri(serverUrl + "/tools/list")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            // 解析并包装为 PromEngine Tool
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch tools from MCP server {}", serverUrl, e);
            return List.of();
        }
    }

    public Object callTool(String serverUrl, String toolName, Map<String, Object> params) {
        return webClient.post()
                .uri(serverUrl + "/tools/call")
                .bodyValue(Map.of("name", toolName, "arguments", params))
                .retrieve()
                .bodyToMono(Object.class)
                .block();
    }
}