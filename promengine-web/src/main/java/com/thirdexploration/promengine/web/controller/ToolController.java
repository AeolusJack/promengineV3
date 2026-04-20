package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.executor.tool.registry.ToolRegistry;
import com.thirdexploration.promengine.executor.tool.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolRegistry toolRegistry;

    @GetMapping
    public Set<String> listToolNames() {
        return toolRegistry.getRegisteredToolNames();
    }

    @GetMapping("/{name}")
    public ToolDefinition getTool(@PathVariable String name) {
        return toolRegistry.resolve(name, null)
                .map(tool -> tool.definition())
                .orElse(null);
    }
}