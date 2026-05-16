package com.thirdexploration.promengine.web.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.memory.agent.model.AgentWorkflow;
import com.thirdexploration.promengine.memory.agent.repository.AgentWorkflowRepository;
import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 前端使用 dagre-d3 或 Cytoscape.js 等库渲染流程图，直观展示工作流步骤和依赖
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final AgentWorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/{id}/dag")
    public ApiResponse<Map<String, Object>> getDag(@PathVariable String id) {
        AgentWorkflow workflow = workflowRepository.findById(id);
        if (workflow == null) return ApiResponse.error("工作流不存在");

        // 解析步骤 JSON
        List<Map<String, Object>> steps;
        try {
            steps = objectMapper.readValue(workflow.getSteps(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return ApiResponse.error("步骤解析失败");
        }

        // 构建节点和边
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        String prevId = null;
        String currentParallelGroup = null;

        for (Map<String, Object> step : steps) {
            String stepId = (String) step.get("id");
            String desc = (String) step.getOrDefault("description", stepId);
            String group = (String) step.get("parallelGroup");

            nodes.add(Map.of("id", stepId, "label", desc, "group", group != null ? group : ""));

            if (group != null) {
                // 并行组：每个节点都连到上一个非并行节点
                if (!group.equals(currentParallelGroup)) {
                    currentParallelGroup = group;
                    prevId = findLastNonParallelId(steps, stepId);
                }
            }

            if (prevId != null) {
                edges.add(Map.of("from", prevId, "to", stepId));
            }
            if (group == null) {
                prevId = stepId;
                currentParallelGroup = null;
            }
        }

        Map<String, Object> result = Map.of("nodes", nodes, "edges", edges);
        return ApiResponse.ok(result);
    }

    private String findLastNonParallelId(List<Map<String, Object>> steps, String beforeId) {
        String lastNonParallel = null;
        for (Map<String, Object> step : steps) {
            String sid = (String) step.get("id");
            if (sid.equals(beforeId)) break;
            if (step.get("parallelGroup") == null) {
                lastNonParallel = sid;
            }
        }
        return lastNonParallel;
    }
}