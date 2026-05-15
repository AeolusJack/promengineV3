package com.thirdexploration.promengine.agent.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.agent.TaskPlan;
import com.thirdexploration.promengine.core.agent.TaskPlanningStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.*;



@Slf4j
@RequiredArgsConstructor
@Component
public class LLMTaskPlanningStrategy implements TaskPlanningStrategy {
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    @Override
    public List<TaskPlan.Step> generatePlan(String userIntent, Map<String, Object> context) {
        String prompt = buildPlanningPrompt(userIntent, context);
        String response = chatClientBuilder.build()
                .prompt(prompt)
                .call()
                .content();
        // 解析 JSON 返回 TaskPlan.Step 列表
        return parsePlanFromJson(response);
    }
    
    private String buildPlanningPrompt(String userIntent, Map<String, Object> context) {
        // 此处可以从配置或模板中读取 Prompt，而不是写死
        return """
            你是一个专业的技术架构师。根据用户的请求，生成一个详细的任务执行计划。
            计划格式为 JSON 数组，每个元素包含：id, tool(工具名), args(参数), description(描述)。
            用户请求：%s
            当前项目上下文：%s
            只返回 JSON，不要包含任何解释。
            """.formatted(userIntent, context.toString());
    }


    private List<TaskPlan.Step> parsePlanFromJson(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        String json = extractJson(response);

        try {
            // 解析为 Step 列表
            List<Map<String, Object>> rawSteps = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});

            return rawSteps.stream()
                    .map(this::toStep)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to parse task plan JSON: {}", json, e);
            return List.of();
        }
    }

    /**
     * 从响应中提取纯 JSON，处理常见的 Markdown 代码块包裹。
     */
    private String extractJson(String text) {
        // 去除首尾空白
        String trimmed = text.trim();

        // 情况1：```json { ... } ```
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
        Matcher matcher = pattern.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 情况2：直接以 [ 或 { 开头
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return trimmed;
        }

        // 情况3：文本中间包含 JSON 数组
        int start = Math.max(trimmed.indexOf("["), trimmed.indexOf("{"));
        if (start >= 0) {
            int end = Math.max(trimmed.lastIndexOf("]"), trimmed.lastIndexOf("}"));
            if (end > start) {
                return trimmed.substring(start, end + 1);
            }
        }

        // 无法识别，返回原文本
        return trimmed;
    }

    /**
     * 将 Map 转换为 TaskPlan.Step 对象。
     */
    private TaskPlan.Step toStep(Map<String, Object> map) {
        try {
            return TaskPlan.Step.builder()
                    .id((String) map.getOrDefault("id", UUID.randomUUID().toString()))
                    .tool((String) map.get("tool"))
                    .args(parseArgs(map.get("args")))
                    .description((String) map.getOrDefault("description", ""))
                    .parallelGroup((String) map.get("parallelGroup"))
                    .maxRetries(map.containsKey("maxRetries") ? ((Number) map.get("maxRetries")).intValue() : 3)
                    .fallback((String) map.get("fallback"))
                    .build();
        } catch (Exception e) {
            log.warn("Invalid step data: {}", map, e);
            return null;
        }
    }

    /**
     * 解析工具参数，args 可能是 Map 或 String。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(Object argsObj) {
        if (argsObj == null) return Map.of();
        if (argsObj instanceof Map) {
            return (Map<String, Object>) argsObj;
        }
        if (argsObj instanceof String) {
            try {
                return objectMapper.readValue((String) argsObj, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse args string: {}", argsObj, e);
                return Map.of();
            }
        }
        return Map.of();
    }

}