package com.thirdexploration.promengine.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.tenant.TenantContext;
import com.thirdexploration.promengine.skill.model.SkillRecord;
import com.thirdexploration.promengine.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;

    // ========== 租户内操作 ==========

    /**
     * 获取当前租户下所有技能
     */
    public List<Map<String, Object>> getAllSkills() {
        String tenantId = TenantContext.getOrDefault();
        List<SkillRecord> skills = skillRepository.findAllByTenant(tenantId);
        return skills.stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定 ID 的技能（必须属于当前租户或已发布到市场）
     */
    public Map<String, Object> getSkillById(String id) {
        SkillRecord record = skillRepository.findById(id);
        if (record == null) {
            throw new NoSuchElementException("Skill not found");
        }
        String tenantId = TenantContext.getOrDefault();
        if (!record.getTenantId().equals(tenantId) && !record.isPublished()) {
            throw new SecurityException("Access denied");
        }
        return toMap(record);
    }

    /**
     * 创建技能
     */
    public Map<String, Object> createSkill(Map<String, Object> body) throws Exception {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name is required");
        }
        String tenantId = TenantContext.getOrDefault();
        SkillRecord record = SkillRecord.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .name(name)
                .description((String) body.getOrDefault("description", ""))
                .version((String) body.getOrDefault("version", "1.0.0"))
                .source((String) body.getOrDefault("source", "custom"))
                .content((String) body.getOrDefault("content", ""))
                .enabled(true)
                .published(false)
                .associatedAgents(objectMapper.writeValueAsString(
                        body.getOrDefault("associatedAgents", new ArrayList<>())))
                .parameters(objectMapper.writeValueAsString(
                        body.getOrDefault("parameters", new HashMap<>())))
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .build();
        skillRepository.save(record);
        return toMap(record);
    }

    /**
     * 更新技能
     */
    public Map<String, Object> updateSkill(String id, Map<String, Object> body) throws Exception {
        SkillRecord existing = skillRepository.findById(id);
        if (existing == null) {
            throw new NoSuchElementException("Skill not found");
        }
        String tenantId = TenantContext.getOrDefault();
        if (!existing.getTenantId().equals(tenantId)) {
            throw new SecurityException("Access denied");
        }

        if (body.containsKey("name")) existing.setName((String) body.get("name"));
        if (body.containsKey("description")) existing.setDescription((String) body.get("description"));
        if (body.containsKey("version")) existing.setVersion((String) body.get("version"));
        if (body.containsKey("source")) existing.setSource((String) body.get("source"));
        if (body.containsKey("content")) existing.setContent((String) body.get("content"));
        if (body.containsKey("enabled")) existing.setEnabled((Boolean) body.get("enabled"));
        if (body.containsKey("associatedAgents")) {
            existing.setAssociatedAgents(objectMapper.writeValueAsString(body.get("associatedAgents")));
        }
        if (body.containsKey("parameters")) {
            existing.setParameters(objectMapper.writeValueAsString(body.get("parameters")));
        }
        // 不直接修改 published，需通过市场接口操作
        existing.setUpdatedAt(System.currentTimeMillis());
        skillRepository.update(existing);
        return toMap(existing);
    }

    /**
     * 删除技能
     */
    public void deleteSkill(String id) {
        SkillRecord existing = skillRepository.findById(id);
        if (existing == null) {
            throw new NoSuchElementException("Skill not found");
        }
        String tenantId = TenantContext.getOrDefault();
        if (!existing.getTenantId().equals(tenantId)) {
            throw new SecurityException("Access denied");
        }
        skillRepository.deleteById(id);
    }

    /**
     * 启用/禁用技能
     */
    public Map<String, Object> toggleSkill(String id, boolean enabled) {
        SkillRecord existing = skillRepository.findById(id);
        if (existing == null) {
            throw new NoSuchElementException("Skill not found");
        }
        String tenantId = TenantContext.getOrDefault();
        if (!existing.getTenantId().equals(tenantId)) {
            throw new SecurityException("Access denied");
        }
        skillRepository.toggleEnabled(id, enabled);
        existing.setEnabled(enabled);
        return toMap(existing);
    }

    /**
     * 根据 ID 获取原始 SkillRecord（内部调用）
     */
    public SkillRecord findById(String id) {
        return skillRepository.findById(id);
    }

    // ========== 市场相关操作 ==========

    /**
     * 获取所有已发布到市场的技能（跨租户）
     */
    public List<Map<String, Object>> listPublished() {
        List<SkillRecord> records = skillRepository.findPublished();
        return records.stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    /**
     * 从市场安装技能到当前租户
     */
    public Map<String, Object> installFromMarket(String sourceSkillId, String userId) {
        SkillRecord source = skillRepository.findById(sourceSkillId);
        if (source == null || !source.isPublished()) {
            throw new NoSuchElementException("Skill not available for installation");
        }
        String targetTenantId = TenantContext.getOrDefault();
        // 复制一份到当前租户
        SkillRecord copy = SkillRecord.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(targetTenantId)
                .name(source.getName() + " (from market)")
                .description(source.getDescription())
                .version(source.getVersion())
                .source(source.getSource())
                .content(source.getContent())
                .enabled(true)
                .published(false)
                .associatedAgents("[]")      // 新实例不关联原 Agent
                .parameters(source.getParameters())
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .build();
        skillRepository.save(copy);
        log.info("Skill '{}' installed from market to tenant '{}' by user '{}'", sourceSkillId, targetTenantId, userId);
        return toMap(copy);
    }

    /**
     * 发布/取消发布技能到市场（仅技能所有者可操作）
     */
    public void setPublished(String id, String userId, boolean published) {
        SkillRecord record = skillRepository.findById(id);
        if (record == null) {
            throw new NoSuchElementException("Skill not found");
        }
        // 仅允许技能所属租户的成员发布（这里简化权限检查，可根据团队管理员扩展）
        // 当前简化为只要是同租户即可
        String tenantId = TenantContext.getOrDefault();
        if (!record.getTenantId().equals(tenantId)) {
            throw new SecurityException("Access denied");
        }
        skillRepository.updatePublished(id, published);
        log.info("Skill '{}' published status changed to {} by user '{}'", id, published, userId);
    }

    // ========== 辅助方法 ==========

    private Map<String, Object> toMap(SkillRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId());
        map.put("tenantId", record.getTenantId());
        map.put("name", record.getName());
        map.put("description", record.getDescription());
        map.put("version", record.getVersion());
        map.put("source", record.getSource());
        map.put("content", record.getContent());
        map.put("enabled", record.isEnabled());
        map.put("published", record.isPublished());
        try {
            map.put("associatedAgents", objectMapper.readValue(
                    record.getAssociatedAgents(), List.class));
        } catch (Exception e) {
            map.put("associatedAgents", new ArrayList<>());
        }
        try {
            map.put("parameters", objectMapper.readValue(
                    record.getParameters(), Map.class));
        } catch (Exception e) {
            map.put("parameters", new HashMap<>());
        }
        map.put("createdAt", record.getCreatedAt());
        map.put("updatedAt", record.getUpdatedAt());
        return map;
    }
}