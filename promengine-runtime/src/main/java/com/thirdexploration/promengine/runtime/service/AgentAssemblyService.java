package com.thirdexploration.promengine.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.memory.agent.model.*;
import com.thirdexploration.promengine.memory.agent.repository.*;
import com.thirdexploration.promengine.runtime.model.AgentRecord;
import com.thirdexploration.promengine.runtime.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 根据 Agent 模板（AgentTemplate）一键创建完整的 Agent 实例，包括基础信息、工具绑定、知识源、工作流关联、提示词覆盖等。这使得“从市场安装 Agent”或“克隆 Agent”成为可能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAssemblyService {

    private final AgentRepository agentRepository;
    private final AgentToolBindingRepository toolBindingRepository;
    private final AgentKnowledgeBaseRepository knowledgeBaseRepository;
    private final AgentWorkflowRepository workflowRepository;
    private final AgentTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    /**
     * 从模板创建完整 Agent 并持久化。
     * @param templateId 模板 ID
     * @param userId     创建者
     * @param customName 自定义名称（可选）
     * @param overrides  覆盖配置（如覆盖系统提示词、工具列表等）
     * @return 新创建的 AgentRecord
     */
    @Transactional
    public AgentRecord assembleFromTemplate(String templateId, String userId,
                                            String customName, Map<String, Object> overrides) throws JsonProcessingException {
        AgentTemplate template = templateRepository.findById(templateId);
        if (template == null) throw new IllegalArgumentException("模板不存在: " + templateId);

        // 解析模板配置
        Map<String, Object> config;
        try {
            config = objectMapper.readValue(template.getTemplateConfig(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("模板配置解析失败", e);
        }

        // 应用 overrides（如果提供）
        if (overrides != null && !overrides.isEmpty()) {
            deepMerge(config, overrides);
        }

        // 1. 创建 AgentRecord
        AgentRecord agent = AgentRecord.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .name(customName != null ? customName : (String) config.getOrDefault("name", "新Agent"))
                .description((String) config.getOrDefault("description", ""))
                .systemPrompt((String) config.getOrDefault("systemPrompt", ""))
                .modelPreference((String) config.getOrDefault("modelPreference", null))
                .memoryDomain((String) config.getOrDefault("memoryDomain", "general"))
                .proactiveLevel((String) config.getOrDefault("proactiveLevel", "none"))
                .visibility("private")
                .enabled(true)
                .createdBy(userId)
                .createdAt(System.currentTimeMillis())
                .build();
        agentRepository.save(agent);

        // 2. 绑定工具
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) config.getOrDefault("tools", List.of());
        for (Map<String, Object> toolConfig : tools) {
            AgentToolBinding binding = AgentToolBinding.builder()
                    .id(UUID.randomUUID().toString())
                    .agentId(agent.getId())
                    .toolName((String) toolConfig.get("name"))
                    .config(objectMapper.writeValueAsString(toolConfig.getOrDefault("config", Map.of())))
                    .enabled(true)
                    .createdAt(System.currentTimeMillis())
                    .build();
            toolBindingRepository.save(binding);
        }

        // 3. 绑定知识源
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> knowledges = (List<Map<String, Object>>) config.getOrDefault("knowledgeBases", List.of());
        for (Map<String, Object> kbConfig : knowledges) {
            AgentKnowledgeBase kb = AgentKnowledgeBase.builder()
                    .id(UUID.randomUUID().toString())
                    .agentId(agent.getId())
                    .name((String) kbConfig.get("name"))
                    .type((String) kbConfig.get("type"))
                    .config(objectMapper.writeValueAsString(kbConfig.getOrDefault("config", Map.of())))
                    .priority(kbConfig.containsKey("priority") ? ((Number) kbConfig.get("priority")).intValue() : 0)
                    .enabled(true)
                    .createdAt(System.currentTimeMillis())
                    .updatedAt(System.currentTimeMillis())
                    .build();
            knowledgeBaseRepository.save(kb);
        }

        // 4. 关联工作流（如果指定）
        String workflowId = (String) config.get("workflowTemplateId");
        if (workflowId != null) {
            // AgentRecord 需要增加字段 workflowTemplateId，已预留
            agent.setWorkflowTemplateId(workflowId);
            agentRepository.update(agent);
        }

        // 5. 增加模板下载次数
        templateRepository.incrementDownloads(templateId);

        log.info("从模板 {} 成功装配 Agent: {}", templateId, agent.getId());
        return agent;
    }

    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> base, Map<String, Object> override) {
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            Object baseValue = base.get(entry.getKey());
            if (baseValue instanceof Map && entry.getValue() instanceof Map) {
                deepMerge((Map<String, Object>) baseValue, (Map<String, Object>) entry.getValue());
            } else {
                base.put(entry.getKey(), entry.getValue());
            }
        }
    }
}