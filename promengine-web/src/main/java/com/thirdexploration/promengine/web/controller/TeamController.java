package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import com.thirdexploration.promengine.runtime.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {
    private final TeamService teamService;

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                                   @RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(teamService.createTeam(body, userId));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listMine(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(teamService.listUserTeams(userId));
    }

    @GetMapping("/{teamId}/members")
    public ApiResponse<List<Map<String, Object>>> members(@PathVariable String teamId) {
        return ApiResponse.ok(teamService.getMembers(teamId));
    }

    @PostMapping("/{teamId}/members")
    public ApiResponse<Void> addMember(@PathVariable String teamId, @RequestBody Map<String, String> body) {
        teamService.addMember(teamId, body.get("userId"), body.getOrDefault("role", "member"));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable String teamId, @PathVariable String userId) {
        teamService.removeMember(teamId, userId);
        return ApiResponse.ok(null);
    }
}