package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.core.ToolInfoProvider;
import com.thirdexploration.promengine.executor.tool.registry.ToolRegistry;
import com.thirdexploration.promengine.executor.tool.registry.ToolDefinition;
import com.thirdexploration.promengine.web.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolRegistry toolRegistry;

//    @GetMapping
//    public Set<String> listToolNames() {
//        return toolRegistry.getRegisteredToolNames();
//    }

    @GetMapping("/{name}")
    public ToolDefinition getTool(@PathVariable String name) {
        return toolRegistry.resolve(name, null)
                .map(tool -> tool.definition())
                .orElse(null);
    }


    /**
     * 获取工具完整列表（含描述、分类、参数等）
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolInfoProvider.ToolInfo info : toolRegistry.getAvailableTools()) {
            String name = info.name();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("description", info.description());

            toolRegistry.resolve(name, null).ifPresent(reg -> {
                ToolDefinition def = reg.definition();
                map.put("category", def.getCategory().name());
                map.put("location", def.getLocation().name());
                map.put("enabled", def.isEnabled());
                List<Map<String, Object>> params = def.getParameters().stream()
                        .map(p -> {
                            Map<String, Object> pm = new LinkedHashMap<>();
                            pm.put("name", p.getName());
                            pm.put("type", p.getType());
                            pm.put("description", p.getDescription());
                            pm.put("required", p.isRequired());
                            pm.put("example", p.getExample());
                            return pm;
                        })
                        .toList();
                map.put("parameters", params);
            });

            tools.add(map);
        }
        return ApiResponse.ok(tools);
    }

    /**
     * 仅获取工具名称列表（供下拉选择等场景使用）
     */
    @GetMapping("/names")
    public ApiResponse<List<String>> listToolNames() {
        List<String> names = toolRegistry.getAvailableTools().stream()
                .map(ToolInfoProvider.ToolInfo::name)
                .toList();
        return ApiResponse.ok(names);
    }
}