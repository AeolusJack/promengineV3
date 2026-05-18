package com.thirdexploration.promengine.runtime.repository;

import com.thirdexploration.promengine.core.tenant.TenantContext;
import com.thirdexploration.promengine.runtime.model.AgentRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class AgentRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ========== 租户内基本操作 ==========

    /**
     * 查询当前租户下指定用户的所有启用的 Agent
     */
    public List<AgentRecord> findAllByUser(String userId) {
        String sql = "SELECT * FROM agents WHERE tenant_id = ? AND user_id = ? AND enabled = 1 ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new AgentRowMapper(), TenantContext.getOrDefault(), userId);
    }

    /**
     * 查询当前租户下所有已发布的公共 Agent（供市场浏览）
     * 注意：published=1 的 Agent 是市场项，不限制用户
     */
    public List<AgentRecord> findPublic() {
        String sql = "SELECT * FROM agents WHERE tenant_id = ? AND visibility = 'public' AND enabled = 1 ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new AgentRowMapper(), TenantContext.getOrDefault());
    }

    /**
     * 根据 ID 查询 Agent（仅限当前租户）
     */
    public AgentRecord findById(String id) {
        String sql = "SELECT * FROM agents WHERE id = ? AND tenant_id = ?";
        return jdbcTemplate.queryForObject(sql, new AgentRowMapper(), id, TenantContext.getOrDefault());
    }

    /**
     * 保存 Agent
     */
    public void save(AgentRecord agent) {
        String sql = "INSERT INTO agents (id, tenant_id, user_id, name, description, avatar, mode, is_independent, " +
                "system_prompt, skills, tools, proactive_level, schedule, model_preference, memory_domain, " +
                "visibility, enabled, published, created_by, created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        jdbcTemplate.update(sql,
                agent.getId(),
                TenantContext.getOrDefault(),
                agent.getUserId(),
                agent.getName(),
                agent.getDescription(),
                agent.getAvatar(),
                agent.getMode(),
                agent.isIndependent() ? 1 : 0,
                agent.getSystemPrompt(),
                agent.getSkills(),
                agent.getTools(),
                agent.getProactiveLevel(),
                agent.getSchedule(),
                agent.getModelPreference(),
                agent.getMemoryDomain(),
                agent.getVisibility(),
                agent.isEnabled() ? 1 : 0,
                agent.isPublished() ? 1 : 0,
                agent.getCreatedBy(),
                agent.getCreatedAt()
        );
    }

    /**
     * 更新 Agent
     */
    public void update(AgentRecord agent) {
        String sql = "UPDATE agents SET name=?, description=?, avatar=?, mode=?, is_independent=?, " +
                "system_prompt=?, skills=?, tools=?, proactive_level=?, schedule=?, model_preference=?, " +
                "memory_domain=?, visibility=?, enabled=?, published=? " +
                "WHERE id=? AND tenant_id=?";
        jdbcTemplate.update(sql,
                agent.getName(),
                agent.getDescription(),
                agent.getAvatar(),
                agent.getMode(),
                agent.isIndependent() ? 1 : 0,
                agent.getSystemPrompt(),
                agent.getSkills(),
                agent.getTools(),
                agent.getProactiveLevel(),
                agent.getSchedule(),
                agent.getModelPreference(),
                agent.getMemoryDomain(),
                agent.getVisibility(),
                agent.isEnabled() ? 1 : 0,
                agent.isPublished() ? 1 : 0,
                agent.getId(),
                TenantContext.getOrDefault()
        );
    }

    /**
     * 删除 Agent
     */
    public void delete(String id) {
        String sql = "DELETE FROM agents WHERE id = ? AND tenant_id = ?";
        jdbcTemplate.update(sql, id, TenantContext.getOrDefault());
    }

    /**
     * 启用/禁用 Agent
     */
    public void toggleEnabled(String id, boolean enabled) {
        String sql = "UPDATE agents SET enabled = ? WHERE id = ? AND tenant_id = ?";
        jdbcTemplate.update(sql, enabled ? 1 : 0, id, TenantContext.getOrDefault());
    }

    // ========== 市场相关操作（跨租户） ==========

    /**
     * 查询所有已发布的 Agent（跨租户，用于市场展示）
     */
    public List<AgentRecord> findPublished() {
        String sql = "SELECT * FROM agents WHERE published = 1 AND enabled = 1 ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new AgentRowMapper());
    }

    /**
     * 更新 Agent 的发布状态
     */
    public void updatePublished(String id, boolean published) {
        String sql = "UPDATE agents SET published = ? WHERE id = ? AND tenant_id = ?";
        jdbcTemplate.update(sql, published ? 1 : 0, id, TenantContext.getOrDefault());
    }

    // ========== 内部 Mapper ==========

    private static class AgentRowMapper implements RowMapper<AgentRecord> {
        @Override
        public AgentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AgentRecord.builder()
                    .id(rs.getString("id"))
                    .tenantId(rs.getString("tenant_id"))
                    .userId(rs.getString("user_id"))
                    .name(rs.getString("name"))
                    .description(rs.getString("description"))
                    .avatar(rs.getString("avatar"))
                    .mode(rs.getString("mode"))
                    .isIndependent(rs.getInt("is_independent") == 1)
                    .systemPrompt(rs.getString("system_prompt"))
                    .skills(rs.getString("skills"))
                    .tools(rs.getString("tools"))
                    .proactiveLevel(rs.getString("proactive_level"))
                    .schedule(rs.getString("schedule"))
                    .modelPreference(rs.getString("model_preference"))
                    .memoryDomain(rs.getString("memory_domain"))
                    .visibility(rs.getString("visibility"))
                    .enabled(rs.getInt("enabled") == 1)
                    .published(rs.getInt("published") == 1)
                    .createdBy(rs.getString("created_by"))
                    .createdAt(rs.getLong("created_at"))
                    .build();
        }
    }
}