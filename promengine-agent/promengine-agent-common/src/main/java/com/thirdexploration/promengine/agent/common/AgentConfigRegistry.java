package com.thirdexploration.promengine.agent.common;

import com.thirdexploration.promengine.runtime.model.AgentRecord;
import com.thirdexploration.promengine.runtime.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Service
public class AgentConfigRegistry {
    private final AgentRepository agentRepository;
    private final Map<String, AgentRecord> cache = new ConcurrentHashMap<>();

    public AgentRecord getConfig(String agentId) {
        return cache.computeIfAbsent(agentId, id -> {
            AgentRecord record = agentRepository.findById(id);
            if (record == null) throw new NoSuchElementException("Agent not found: " + id);
            // 此处可加载关联的工具绑定、知识源等
            return record;
        });
    }

    public void refresh(String agentId) {
        cache.remove(agentId);
    }
}