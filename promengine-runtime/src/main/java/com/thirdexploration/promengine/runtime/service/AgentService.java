package com.thirdexploration.promengine.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.runtime.model.AgentRecord;
import com.thirdexploration.promengine.runtime.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AgentService {
    private final AgentRepository agentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> listForUser(String userId, String visibility) {
        List<AgentRecord> records;
        if ("public".equalsIgnoreCase(visibility)) {
            records = agentRepository.findPublic();
        } else {
            records = agentRepository.findAllByUser(userId);
            if ("all".equalsIgnoreCase(visibility)) {
                List<AgentRecord> publicAgents = agentRepository.findPublic();
                Set<String> ownIds = new HashSet<>();
                records.forEach(a -> ownIds.add(a.getId()));
                publicAgents.forEach(a -> { if (!ownIds.contains(a.getId())) records.add(a); });
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (AgentRecord r : records) {
            list.add(toMap(r));
        }
        return list;
    }

    public Map<String, Object> getAgent(String userId, String id) {
        AgentRecord r = agentRepository.findById(id);
        if (r != null && (r.getVisibility().equals("public") || r.getUserId().equals(userId)))
            return toMap(r);
        throw new NoSuchElementException("Agent not found");
    }

    public Map<String, Object> createAgent(String userId, Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name required");
        AgentRecord record = AgentRecord.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .name(name)
                .description((String) body.getOrDefault("description", ""))
                .avatar((String) body.getOrDefault("avatar", ""))
                .mode((String) body.getOrDefault("mode", "silicon"))
                .isIndependent((Boolean) body.getOrDefault("isIndependent", false))
                .systemPrompt((String) body.getOrDefault("systemPrompt", ""))
                .skills(toJson(body.getOrDefault("skills", List.of())))
                .tools(toJson(body.getOrDefault("tools", List.of())))
                .proactiveLevel((String) body.getOrDefault("proactiveLevel", "none"))
                .schedule((String) body.getOrDefault("schedule", null))
                .modelPreference((String) body.getOrDefault("modelPreference", null))
                .memoryDomain((String) body.getOrDefault("memoryDomain", "general"))
                .visibility((String) body.getOrDefault("visibility", "private"))
                .createdBy((String) body.getOrDefault("createdBy", "frontend"))
                .enabled(true)
                .createdAt(System.currentTimeMillis())
                .build();
        agentRepository.save(record);
        return toMap(record);
    }

    public Map<String, Object> updateAgent(String userId, String id, Map<String, Object> body) {
        AgentRecord r = agentRepository.findById(id);
        if (!r.getUserId().equals(userId)) throw new SecurityException("Access denied");
        if (body.containsKey("name")) r.setName((String) body.get("name"));
        if (body.containsKey("description")) r.setDescription((String) body.get("description"));
        if (body.containsKey("avatar")) r.setAvatar((String) body.get("avatar"));
        if (body.containsKey("mode")) r.setMode((String) body.get("mode"));
        if (body.containsKey("isIndependent")) r.setIndependent((Boolean) body.get("isIndependent"));
        if (body.containsKey("systemPrompt")) r.setSystemPrompt((String) body.get("systemPrompt"));
        if (body.containsKey("skills")) r.setSkills(toJson(body.get("skills")));
        if (body.containsKey("tools")) r.setTools(toJson(body.get("tools")));
        if (body.containsKey("proactiveLevel")) r.setProactiveLevel((String) body.get("proactiveLevel"));
        if (body.containsKey("schedule")) r.setSchedule((String) body.get("schedule"));
        if (body.containsKey("modelPreference")) r.setModelPreference((String) body.get("modelPreference"));
        if (body.containsKey("memoryDomain")) r.setMemoryDomain((String) body.get("memoryDomain"));
        if (body.containsKey("visibility")) r.setVisibility((String) body.get("visibility"));
        if (body.containsKey("enabled")) r.setEnabled((Boolean) body.get("enabled"));
        agentRepository.update(r);
        return toMap(r);
    }

    public void deleteAgent(String userId, String id) {
        AgentRecord r = agentRepository.findById(id);
        if (!r.getUserId().equals(userId)) throw new SecurityException("Access denied");
        agentRepository.delete(id);
    }

    public Map<String, Object> toggleAgent(String userId, String id, boolean enabled) {
        AgentRecord r = agentRepository.findById(id);
        if (!r.getUserId().equals(userId)) throw new SecurityException("Access denied");
        agentRepository.toggleEnabled(id, enabled);
        r.setEnabled(enabled);
        return toMap(r);
    }

    /**
     * 根据 ID 直接获取 AgentRecord 对象，不检查权限。
     * 供内部调用（如 ChatController 需要完整 Agent 配置）。
     * @param id Agent ID
     * @return AgentRecord 或 null（如果不存在）
     */
    public AgentRecord getAgentRecord(String id) {
        try {
            return agentRepository.findById(id);
        } catch (Exception e) {
            // 捕获可能的 EmptyResultDataAccessException，返回 null
            return null;
        }
    }

    private Map<String, Object> toMap(AgentRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("userId", r.getUserId());
        m.put("name", r.getName());
        m.put("description", r.getDescription());
        m.put("avatar", r.getAvatar());
        m.put("mode", r.getMode());
        m.put("isIndependent", r.isIndependent());
        m.put("systemPrompt", r.getSystemPrompt());
        try { m.put("skills", objectMapper.readValue(r.getSkills(), List.class)); } catch (Exception e) { m.put("skills", List.of()); }
        try { m.put("tools", objectMapper.readValue(r.getTools(), List.class)); } catch (Exception e) { m.put("tools", List.of()); }
        m.put("proactiveLevel", r.getProactiveLevel());
        m.put("schedule", r.getSchedule());
        m.put("modelPreference", r.getModelPreference());
        m.put("memoryDomain", r.getMemoryDomain());
        m.put("visibility", r.getVisibility());
        m.put("enabled", r.isEnabled());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "[]"; }
    }
}