package com.thirdexploration.promengine.evolution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Agent 工厂：基于配置生成新的 Agent 实例（自指涉演进）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentFactory {

    private final ObjectMapper objectMapper;
    private final Path agentsConfigDir = Path.of("./configs/agents");

    /**
     * 生成新 Agent 配置文件
     */
    public String generateAgentConfig(String name, Map<String, Object> traits, String parentId) {
        String agentId = IdGenerator.generateWithPrefix("agent");
        Map<String, Object> config = Map.of(
                "id", agentId,
                "name", name,
                "parentId", parentId,
                "traits", traits,
                "createdAt", System.currentTimeMillis()
        );
        try {
            Files.createDirectories(agentsConfigDir);
            Path configFile = agentsConfigDir.resolve(agentId + ".json");
            objectMapper.writeValue(configFile.toFile(), config);
            log.info("Generated new agent config: {}", agentId);
            return agentId;
        } catch (Exception e) {
            log.error("Failed to generate agent config", e);
            throw new RuntimeException("Agent generation failed", e);
        }
    }
}