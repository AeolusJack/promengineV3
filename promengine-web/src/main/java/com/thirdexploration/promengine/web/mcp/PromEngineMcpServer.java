package com.thirdexploration.promengine.web.mcp;

import com.thirdexploration.promengine.executor.tool.registry.ToolRegistry;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mcp/v1")
public class PromEngineMcpServer {
    private final ToolRegistry toolRegistry;
    private final UnifiedMemoryAPI memoryAPI;

    public PromEngineMcpServer(ToolRegistry toolRegistry, UnifiedMemoryAPI memoryAPI) {
        this.toolRegistry = toolRegistry;
        this.memoryAPI = memoryAPI;
    }

    @GetMapping("/tools/list")
    public List<Map<String, String>> listTools() {
        return toolRegistry.getAvailableTools().stream()
                .map(tool -> Map.of("name", tool.name(), "description", tool.description()))
                .toList();
    }

    @PostMapping("/tools/call")
    public Object callTool(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        Map<String, Object> args = (Map<String, Object>) request.get("arguments");
        return toolRegistry.resolve(name, null)
                .map(tool -> {
                    try {
                        return tool.invoker().invoke(args);
                    } catch (Exception e) {
                        return "Error: " + e.getMessage();
                    }
                }).orElse("Tool not found");
    }

    // 可继续暴露记忆检索等接口
}