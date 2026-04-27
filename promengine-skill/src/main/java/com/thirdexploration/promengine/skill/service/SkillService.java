package com.thirdexploration.promengine.skill.service;

import com.thirdexploration.promengine.skill.model.SkillRecord;
import com.thirdexploration.promengine.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;

    public List<Map<String, Object>> getAllSkills() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SkillRecord skill : skillRepository.findAll()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", skill.getId());
            map.put("name", skill.getName());
            map.put("description", skill.getDescription());
            map.put("version", skill.getVersion());
            map.put("source", skill.getSource());
            map.put("content", skill.getContent());
            map.put("enabled", skill.isEnabled());
            // 反序列化 JSON 字段
            try {
                map.put("associatedAgents", new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(skill.getAssociatedAgents(), List.class));
            } catch (Exception e) {
                map.put("associatedAgents", new ArrayList<>());
            }
            try {
                map.put("parameters", new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(skill.getParameters(), Map.class));
            } catch (Exception e) {
                map.put("parameters", new HashMap<>());
            }
            result.add(map);
        }
        return result;
    }

    public Map<String, Object> createSkill(Map<String, Object> body) throws Exception {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Skill name is required");

        SkillRecord record = SkillRecord.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description((String) body.getOrDefault("description", ""))
                .version((String) body.getOrDefault("version", "1.0.0"))
                .source((String) body.getOrDefault("source", "custom"))
                .content((String) body.getOrDefault("content",""))
                .enabled(true)
                .associatedAgents(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                        body.getOrDefault("associatedAgents", new ArrayList<>())))
                .parameters(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                        body.getOrDefault("parameters", new HashMap<>())))
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .build();
        skillRepository.save(record);
        return toMap(record);
    }

    public Map<String, Object> updateSkill(String id, Map<String, Object> body) throws Exception {
        SkillRecord existing = skillRepository.findById(id);
        if (existing == null) throw new NoSuchElementException("Skill not found");
        if (body.containsKey("name")) existing.setName((String) body.get("name"));
        if (body.containsKey("description")) existing.setDescription((String) body.get("description"));
        if (body.containsKey("version")) existing.setVersion((String) body.get("version"));
        if (body.containsKey("source")) existing.setSource((String) body.get("source"));
        if (body.containsKey("content")) existing.setSource((String) body.get("content"));
        if (body.containsKey("enabled")) existing.setEnabled((Boolean) body.get("enabled"));
        if (body.containsKey("associatedAgents")) existing.setAssociatedAgents(
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body.get("associatedAgents")));
        if (body.containsKey("parameters")) existing.setParameters(
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body.get("parameters")));
        existing.setUpdatedAt(System.currentTimeMillis());
        skillRepository.update(existing);
        return toMap(existing);
    }

    public void deleteSkill(String id) {
        skillRepository.deleteById(id);
    }

    public Map<String, Object> toggleSkill(String id, boolean enabled) {
        skillRepository.toggleEnabled(id, enabled);
        return toMap(skillRepository.findById(id));
    }

    private Map<String, Object> toMap(SkillRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId());
        map.put("name", record.getName());
        map.put("description", record.getDescription());
        map.put("version", record.getVersion());
        map.put("source", record.getSource());
        map.put("content", record.getContent());
        map.put("enabled", record.isEnabled());
        try {
            map.put("associatedAgents", new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(record.getAssociatedAgents(), List.class));
        } catch (Exception e) {
            map.put("associatedAgents", new ArrayList<>());
        }
        try {
            map.put("parameters", new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(record.getParameters(), Map.class));
        } catch (Exception e) {
            map.put("parameters", new HashMap<>());
        }
        return map;
    }
}