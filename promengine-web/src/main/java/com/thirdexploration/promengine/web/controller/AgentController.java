package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import com.thirdexploration.promengine.runtime.model.ChatMessage;
import com.thirdexploration.promengine.runtime.repository.AgentGroupRepository;
import com.thirdexploration.promengine.runtime.service.AgentGroupService;
import com.thirdexploration.promengine.runtime.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService agentService;


    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAgents(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "mine") String visibility) {
        return ApiResponse.ok(agentService.listForUser(userId, visibility));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getAgent(
            @RequestHeader("X-User-Id") String userId, @PathVariable String id) {
        try {
            return ApiResponse.ok(agentService.getAgent(userId, id));
        } catch (NoSuchElementException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createAgent(
            @RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.ok(agentService.createAgent(userId, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateAgent(
            @RequestHeader("X-User-Id") String userId, @PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.ok(agentService.updateAgent(userId, id, body));
        } catch (NoSuchElementException | SecurityException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAgent(
            @RequestHeader("X-User-Id") String userId, @PathVariable String id) {
        try {
            agentService.deleteAgent(userId, id);
            return ApiResponse.ok(null);
        } catch (SecurityException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PatchMapping("/{id}/toggle")
    public ApiResponse<Map<String, Object>> toggleAgent(
            @RequestHeader("X-User-Id") String userId, @PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            boolean enabled = (Boolean) body.get("enabled");
            return ApiResponse.ok(agentService.toggleAgent(userId, id, enabled));
        } catch (SecurityException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}