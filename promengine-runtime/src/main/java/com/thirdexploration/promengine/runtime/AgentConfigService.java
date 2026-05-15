package com.thirdexploration.promengine.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.thirdexploration.promengine.core.AgentConfig;
import com.thirdexploration.promengine.core.agent.AgentConfigProvider;
import com.thirdexploration.promengine.runtime.model.AgentRecord;
import com.thirdexploration.promengine.runtime.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigService implements AgentConfigProvider {

    private final AgentRepository agentRepository;
    private final ObjectMapper objectMapper;

    @Override
    public AgentConfig getConfig(String agentId) {
        AgentRecord record;
        try {
            record = agentRepository.findById(agentId);
        } catch (Exception e) {
            log.warn("Agent {} not found", agentId);
            return null;
        }
        if (record == null) return null;

        return AgentConfig.builder()
                .agentId(record.getId())
                .systemPrompt(record.getSystemPrompt())
                .tools(parseTools(record.getTools()))
                .modelPreference(record.getModelPreference())
                .memoryDomain(record.getMemoryDomain())
                .enableHumanReview( record.isEnableHumanReview())
                .maxRetries(record.getMaxRetries() != null ? record.getMaxRetries() : 3)
                .timeoutSeconds(record.getTimeoutSeconds() != null ? record.getTimeoutSeconds() : 300)
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