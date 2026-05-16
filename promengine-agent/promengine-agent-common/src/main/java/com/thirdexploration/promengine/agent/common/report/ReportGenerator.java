package com.thirdexploration.promengine.agent.common.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 通用报告生成器，基于提示词模板和 LLM 生成结构化报告。
 * 适用于所有垂类 Agent 的报告生成需求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerator {

    private final ChatClient.Builder chatClientBuilder;

    /**
     * 根据模板和数据生成报告。
     * @param template  报告模板
     * @param data      报告数据（模板变量）
     * @return 生成的报告内容
     */
    public String generate(ReportTemplate template, Map<String, Object> data) {
        // 1. 填充模板变量
        String prompt = fillTemplate(template.getPromptTemplate(), data);

        // 2. 根据报告类型构建系统提示词
        String systemPrompt = buildSystemPrompt(template.getType());

        // 3. 调用 LLM 生成报告
        try {
            return chatClientBuilder.build()
                    .prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("生成报告失败: {}", e.getMessage());
            return "报告生成失败：" + e.getMessage();
        }
    }

    private String fillTemplate(String template, Map<String, Object> data) {
        String result = template;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }

    private String buildSystemPrompt(ReportTemplate.ReportType type) {
        return switch (type) {
            case MARKDOWN ->
                "你是一个专业的报告生成器。请根据用户提供的数据，生成一份结构清晰、内容详实的 Markdown 格式报告。";
            case HTML ->
                "你是一个专业的报告生成器。请根据用户提供的数据，生成一份格式规范的 HTML 报告。";
            case JSON ->
                "你是一个专业的数据处理专家。请根据用户提供的数据，生成一份严格的 JSON 格式报告，不要包含任何额外说明。";
        };
    }
}