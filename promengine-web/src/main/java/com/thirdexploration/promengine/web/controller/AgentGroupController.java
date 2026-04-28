package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.runtime.model.AgentGroup;
import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import com.thirdexploration.promengine.runtime.model.ChatMessage;
import com.thirdexploration.promengine.runtime.service.AgentGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/agents/groups")
@RequiredArgsConstructor
public class AgentGroupController {
    private final AgentGroupService groupService;

    @GetMapping
    public ApiResponse<List<AgentGroup>> listGroups(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(groupService.listGroups(userId));
    }

    @PostMapping
    public ApiResponse<AgentGroup> createGroup(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        var group = groupService.createGroup(userId, body);
        return ApiResponse.ok(group);
    }


    @GetMapping("/{id}/messages")
    public ApiResponse<List<ChatMessage>> getGroupMessages(@PathVariable String id) {
        return ApiResponse.ok(groupService.getMessages(id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<ChatMessage> sendGroupMessage(@PathVariable String id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(groupService.addMessage(id, body.get("message")));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<Void> startDiscussion(@PathVariable String id) {
        groupService.startDiscussion(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/stop")
    public ApiResponse<Void> stopDiscussion(@PathVariable String id) {
        // 更新状态为 paused
        return ApiResponse.ok(null);
    }
}