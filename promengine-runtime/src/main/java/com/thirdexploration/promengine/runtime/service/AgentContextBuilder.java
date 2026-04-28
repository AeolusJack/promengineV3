package com.thirdexploration.promengine.runtime.service;

import com.thirdexploration.promengine.prompt.core.PromptContext;
import com.thirdexploration.promengine.runtime.model.AgentGroup;
import com.thirdexploration.promengine.runtime.model.AgentRecord;
import com.thirdexploration.promengine.runtime.model.ChatMessage;
import com.thirdexploration.promengine.runtime.model.GroupAgent;
import com.thirdexploration.promengine.runtime.repository.AgentRepository;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class AgentContextBuilder {
    private final AgentRepository agentRepository;
    private final UnifiedMemoryAPI memoryAPI;

    /**
     * 获取 Agent 的完整配置
     */
    public AgentRecord getAgentRecord(String agentId) {
        return agentRepository.findById(agentId);
    }

    /**
     * 为群组中的特定 Agent 构建 PromptContext，包含角色信息、记忆内容等。
     */
    public PromptContext buildGroupPromptContext(AgentGroup group, GroupAgent ga, AgentRecord agent, List<ChatMessage> history) {
        // 1. 检索 Agent 的长期记忆
        List<MemoryEntry> memories = memoryAPI.recall(MemoryQuery.builder()
                .userId(null) // 这里可以用 Agent 的拥有者 ID，或者不填
                .text(group.getTopic())
                .domain(agent.getMemoryDomain())
                .maxResults(3)
                .build());

        // 2. 构建上下文变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("agent_name", ga.getName());
        variables.put("agent_role", ga.getRole() != null ? ga.getRole() : "参与者");
        variables.put("group_topic", group.getTopic());
        variables.put("agent_skills", agent.getSkills() != null ? String.join("\n", agent.getSkills()) : "");
        variables.put("agent_tools", agent.getTools() != null ? String.join("\n", agent.getTools()) : "");
        // 截取最近几条记忆作为背景信息
        variables.put("relevant_memories", truncateMemories(memories));

        // 3. 构建 PromptContext，模板 ID 定为 "agent_group_chat"，模板内容应包含这些变量
        return PromptContext.builder()
                .taskType("agent_group_chat")
                .userInput(agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "你是一个智能助手。")
                .extraVariables(variables)
                .availableTools(Collections.singletonList(agent.getTools())) //需要转为list工具列表
                .memories(memories)
                .build();
    }

    private String truncateMemories(List<MemoryEntry> memories) {
        if (memories.isEmpty()) return "暂无相关记忆。";
        StringBuilder sb = new StringBuilder();
        for (MemoryEntry m : memories) {
            String text = m.getSummary() != null ? m.getSummary() : m.getContent();
            if (text.length() > 100) text = text.substring(0, 100) + "...";
            sb.append("- ").append(text).append("\n");
        }
        return sb.toString();
    }
}