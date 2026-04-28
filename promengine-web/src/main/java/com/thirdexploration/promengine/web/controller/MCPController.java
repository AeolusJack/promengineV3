package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.ecosystem.mcp.McpClientService;
import com.thirdexploration.promengine.ecosystem.mcp.McpServerRecord;
import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
public class MCPController {

    private final McpClientService mcpClientService;

    @GetMapping("/servers")
    public ApiResponse<List<Map<String, Object>>> listServers() {
        return ApiResponse.ok(mcpClientService.getAllServers());
    }

    @PostMapping("/servers")
    public ApiResponse<Map<String, Object>> addServer(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String url = body.get("url");
        if (name == null || url == null) {
            return ApiResponse.error("Name and URL are required");
        }
        McpServerRecord record = mcpClientService.addServer(name, url);
        return ApiResponse.ok(Map.of("id", record.getId(), "name", record.getName(), "url", record.getUrl()));
    }

    @DeleteMapping("/servers/{id}")
    public ApiResponse<Void> deleteServer(@PathVariable String id) {
        mcpClientService.removeServer(id);
        return ApiResponse.ok(null);
    }
}