package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.executor.tool.registry.ToolDefinition;
import com.thirdexploration.promengine.executor.tool.registry.ToolRegistry;
import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import com.thirdexploration.promengine.runtime.service.AgentService;
import com.thirdexploration.promengine.skill.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketController {
    private final AgentService agentService;
    private final SkillService skillService;
    private final ToolRegistry toolRegistry;

    // 获取所有已发布的 Agent
    @GetMapping("/agents")
    public ApiResponse<List<Map<String, Object>>> listPublishedAgents() {
        return ApiResponse.ok(agentService.listPublished());
    }

    // 获取已发布的技能
    @GetMapping("/skills")
    public ApiResponse<List<Map<String, Object>>> listPublishedSkills() {
        return ApiResponse.ok(skillService.listPublished());
    }

    // 获取已发布的工具
    @GetMapping("/tools")
    public ApiResponse<List<ToolDefinition>> listPublishedTools() {
        return ApiResponse.ok(toolRegistry.listPublishedTools());
    }

    // 从市场安装 Agent（复制到当前租户）
    @PostMapping("/agents/{id}/install")
    public ApiResponse<Map<String, Object>> installAgent(@PathVariable String id,
                                                         @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(agentService.installFromMarket(id, userId));
    }

    // 发布/取消发布自己的 Agent
    @PatchMapping("/my/agents/{id}/publish")
    public ApiResponse<Void> togglePublishAgent(@PathVariable String id,
                                                @RequestHeader("X-User-Id") String userId,
                                                @RequestBody Map<String, Boolean> body) {
        boolean publish = body.getOrDefault("published", false);
        agentService.setPublished(id, userId, publish);
        return ApiResponse.ok(null);
    }
}