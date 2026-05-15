package com.thirdexploration.promengine.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.thirdexploration.promengine.core.AgentConfig;
import com.thirdexploration.promengine.core.agent.AgentConfigProvider;
import com.thirdexploration.promengine.memory.agent.model.AgentKnowledgeBase;
import com.thirdexploration.promengine.memory.agent.model.AgentToolBinding;
import com.thirdexploration.promengine.memory.agent.repository.AgentKnowledgeBaseRepository;
import com.thirdexploration.promengine.memory.agent.repository.AgentToolBindingRepository;
import com.thirdexploration.promengine.runtime.model.AgentRecord;
import com.thirdexploration.promengine.runtime.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigService implements AgentConfigProvider {

    private final AgentRepository agentRepository;
    private final ObjectMapper objectMapper;

    private final AgentToolBindingRepository toolBindingRepository;
    private final AgentKnowledgeBaseRepository knowledgeBaseRepository;

//    @Override
//    public AgentConfig getConfig(String agentId) {
//        AgentRecord record;
//        try {
//            record = agentRepository.findById(agentId);
//        } catch (Exception e) {
//            log.warn("Agent {} not found", agentId);
//            return null;
//        }
//        if (record == null) return null;
//
//        return AgentConfig.builder()
//                .agentId(record.getId())
//                .systemPrompt(record.getSystemPrompt())
//                .tools(parseTools(record.getTools()))
//                .modelPreference(record.getModelPreference())
//                .memoryDomain(record.getMemoryDomain())
//                .enableHumanReview( record.isEnableHumanReview())
//                .maxRetries(record.getMaxRetries() != null ? record.getMaxRetries() : 3)
//                .timeoutSeconds(record.getTimeoutSeconds() != null ? record.getTimeoutSeconds() : 300)
//                .build();
//    }

    @Override
    public AgentConfig getConfig(String agentId) {
        AgentRecord record = agentRepository.findById(agentId);
        if (record == null) return null;

        // 从 agent_tool_bindings 表加载工具列表
        List<AgentToolBinding> bindings = toolBindingRepository.findByAgentId(agentId);
        List<String> tools = bindings.stream()
                .map(AgentToolBinding::getToolName)
                .collect(Collectors.toList());

        // 从 agent_knowledge_bases 表加载知识源配置
        List<AgentKnowledgeBase> knowledges = knowledgeBaseRepository.findByAgentId(agentId);
        // 可将其注入到 AgentConfig 的扩展字段中，或合并处理

        return AgentConfig.builder()
                .agentId(record.getId())
                .systemPrompt(record.getSystemPrompt())
                .tools(tools)
                .modelPreference(record.getModelPreference())
                .memoryDomain(record.getMemoryDomain())
                .enableHumanReview( record.isEnableHumanReview())
                .maxRetries(record.getMaxRetries() != null ? record.getMaxRetries() : 3)
                .timeoutSeconds(record.getTimeoutSeconds() != null ? record.getTimeoutSeconds() : 300)
                .planningSkillName(record.getPlanningSkillName())
                .type(record.getType())
                // 可添加工工作流 ID 等
                .build();
    }

    private List<String> parseTools(String toolsJson) {
        if (toolsJson == null || toolsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(toolsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse tools JSON for agent: {}", toolsJson, e);
            return List.of();
        }
    }
}