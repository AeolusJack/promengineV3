package com.thirdexploration.promengine.agent.common.planning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.AgentConfig;
import com.thirdexploration.promengine.core.agent.TaskPlan;
import com.thirdexploration.promengine.core.agent.TaskPlanningStrategy;
import com.thirdexploration.promengine.skill.Skill;
import com.thirdexploration.promengine.skill.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class DelegatingTaskPlanningStrategy implements TaskPlanningStrategy {

    private final ChatClient.Builder chatClientBuilder;
    private final SkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;

    // 默认的 LLM 提示词生成方法（当没有指定 Skill 时使用）
    @Override
    public List<TaskPlan.Step> generatePlan(String userIntent, Map<String, Object> context) {
        // 从 context 中获取 Agent 配置（可选）
        Object agentConfigObj = context.get("agentConfig");
        if (agentConfigObj instanceof AgentConfig agentConfig) {
            String skillName = agentConfig.getPlanningSkillName();
            if (skillName != null && !skillName.isBlank()) {
                return generatePlanViaSkill(skillName, userIntent, context);
            }
        }
        // 默认：LLM 生成
        return generatePlanViaLLM(userIntent, context);
    }

    private List<TaskPlan.Step> generatePlanViaLLM(String userIntent, Map<String, Object> context) {
        String prompt = buildLLMPrompt(userIntent, context);
        String response = chatClientBuilder.build()
                .prompt(prompt)
                .call()
                .content();
        return parsePlan(response);
    }

    private List<TaskPlan.Step> generatePlanViaSkill(String skillName, String userIntent, Map<String, Object> context) {
        Skill skill = skillRegistry.get(skillName);
        if (skill == null) {
            log.warn("规划 Skill {} 未找到，回退到 LLM 模式", skillName);
            return generatePlanViaLLM(userIntent, context);
        }
        try {
            Map<String, Object> input = Map.of("intent", userIntent, "context", context);
            Map<String, Object> result = skill.execute(input);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawSteps = (List<Map<String, Object>>) result.get("steps");
            return rawSteps.stream().map(this::mapToStep).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Skill 规划执行失败，回退到 LLM 模式", e);
            return generatePlanViaLLM(userIntent, context);
        }
    }

    private String buildLLMPrompt(String userIntent, Map<String, Object> context) {
        return """
            你是一个资深架构师。根据用户意图和上下文，生成任务执行计划。
            输出一个 JSON 数组，每个元素包含：
            - id: 步骤编号
            - tool: 工具名
            - args: 工具参数对象
            - description: 步骤说明
            用户意图：%s
            上下文：%s
            只返回 JSON 数组，不要包含任何解释。
            """.formatted(userIntent, context.toString());
    }

    private List<TaskPlan.Step> parsePlan(String response) {
        if (response == null || response.isBlank()) return List.of();
        String json = extractJson(response);
        try {
            List<Map<String, Object>> rawList = objectMapper.readValue(
                    json, new TypeReference<List<Map<String, Object>>>() {});
            return rawList.stream().map(this::mapToStep).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("解析任务计划 JSON 失败: {}", json, e);
            return List.of();
        }
    }

    private String extractJson(String text) {
        String trimmed = text.trim();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "```(?:json)?\\s*([\\s\\S]*?)\\s*```");
        java.util.regex.Matcher matcher = pattern.matcher(trimmed);
        if (matcher.find()) return matcher.group(1).trim();
        if (trimmed.startsWith("[")) return trimmed;
        int start = trimmed.indexOf("[");
        if (start >= 0) {
            int end = trimmed.lastIndexOf("]");
            if (end > start) return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    @SuppressWarnings("unchecked")
    private TaskPlan.Step mapToStep(Map<String, Object> map) {
        Map<String, Object> args;
        Object argsObj = map.get("args");
        if (argsObj instanceof Map) args = (Map<String, Object>) argsObj;
        else if (argsObj instanceof String) {
            try {
                args = objectMapper.readValue((String) argsObj,
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                args = Map.of();
            }
        } else args = Map.of();

        return TaskPlan.Step.builder()
                .id((String) map.getOrDefault("id", UUID.randomUUID().toString()))
                .tool((String) map.get("tool"))
                .args(args)
                .description((String) map.getOrDefault("description", ""))
                .parallelGroup((String) map.get("parallelGroup"))
                .maxRetries(map.containsKey("maxRetries") ? ((Number) map.get("maxRetries")).intValue() : 3)
                .fallback((String) map.get("fallback"))
                .build();
    }
}