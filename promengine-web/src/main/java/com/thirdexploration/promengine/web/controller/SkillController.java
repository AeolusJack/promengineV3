package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.web.dto.ApiResponse;
import com.thirdexploration.promengine.web.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listSkills() {
        return ApiResponse.ok(skillService.getAllSkills());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createSkill(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> skill = skillService.createSkill(body);
            return ApiResponse.ok(skill);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("Failed to create skill: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateSkill(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> skill = skillService.updateSkill(id, body);
            return ApiResponse.ok(skill);
        } catch (NoSuchElementException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("Failed to update skill: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSkill(@PathVariable String id) {
        skillService.deleteSkill(id);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{id}/toggle")
    public ApiResponse<Map<String, Object>> toggleSkill(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            boolean enabled = (Boolean) body.get("enabled");
            return ApiResponse.ok(skillService.toggleSkill(id, enabled));
        } catch (Exception e) {
            return ApiResponse.error("Toggle failed: " + e.getMessage());
        }
    }

    @PostMapping("/install-mcp")
    public ApiResponse<String> installFromMcp(@RequestBody Map<String, String> body) {
        String serverUrl = body.get("serverUrl");
        // 实际实现会连接 MCP 服务并注册工具，目前仅返回成功
        return ApiResponse.ok("MCP install request accepted for " + serverUrl);
    }
}