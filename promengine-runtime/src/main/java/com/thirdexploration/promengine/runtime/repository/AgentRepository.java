package com.thirdexploration.promengine.runtime.repository;

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

    public List<AgentRecord> findAllByUser(String userId) {
        return jdbcTemplate.query(
            "SELECT * FROM agents WHERE user_id = ? AND enabled = 1 ORDER BY created_at DESC",
            new AgentRowMapper(), userId);
    }

    public List<AgentRecord> findPublic() {
        return jdbcTemplate.query(
            "SELECT * FROM agents WHERE visibility = 'public' AND enabled = 1 ORDER BY created_at DESC",
            new AgentRowMapper());
    }

    public AgentRecord findById(String id) {
        return jdbcTemplate.queryForObject("SELECT * FROM agents WHERE id = ?",
                new AgentRowMapper(), id);
    }

    public void save(AgentRecord agent) {
        jdbcTemplate.update(
                "INSERT INTO agents (id, user_id, name, description, avatar, mode, is_independent, system_prompt, skills, tools, proactive_level, schedule, model_preference, memory_domain, visibility, enabled, created_by,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                agent.getId(), agent.getUserId(), agent.getName(), agent.getDescription(),
                agent.getAvatar(), agent.getMode(), agent.isIndependent() ? 1 : 0,
                agent.getSystemPrompt(), agent.getSkills(), agent.getTools(),
                agent.getProactiveLevel(), agent.getSchedule(), agent.getModelPreference(),
                agent.getMemoryDomain(), agent.getVisibility(), agent.isEnabled() ? 1 : 0,
                agent.getCreatedBy(),
                agent.getCreatedAt());
    }

    public void update(AgentRecord agent) {
        jdbcTemplate.update(
                "UPDATE agents SET name=?, description=?, avatar=?, mode=?, is_independent=?, system_prompt=?, skills=?, tools=?, proactive_level=?, schedule=?, model_preference=?, memory_domain=?, visibility=?, enabled=? WHERE id=?",
                agent.getName(), agent.getDescription(), agent.getAvatar(), agent.getMode(),
                agent.isIndependent() ? 1 : 0, agent.getSystemPrompt(), agent.getSkills(),
                agent.getTools(), agent.getProactiveLevel(), agent.getSchedule(),
                agent.getModelPreference(), agent.getMemoryDomain(), agent.getVisibility(),
                agent.isEnabled() ? 1 : 0, agent.getId());
    }

    public void delete(String id) {
        jdbcTemplate.update("DELETE FROM agents WHERE id = ?", id);
    }

    public void toggleEnabled(String id, boolean enabled) {
        jdbcTemplate.update("UPDATE agents SET enabled = ? WHERE id = ?",
                enabled ? 1 : 0, id);
    }

    private static class AgentRowMapper implements RowMapper<AgentRecord> {
        @Override
        public AgentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AgentRecord.builder()
                    .id(rs.getString("id"))
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
                    .createdBy(rs.getString("created_by"))
                    .createdAt(rs.getLong("created_at"))
                    .build();
        }
    }
}