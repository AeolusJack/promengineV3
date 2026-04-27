package com.thirdexploration.promengine.skill.repository;

import com.thirdexploration.promengine.skill.model.SkillRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class SkillRepository {

    private final JdbcTemplate jdbcTemplate;

    public SkillRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SkillRecord> findAll() {
        String sql = "SELECT * FROM skills ORDER BY updated_at DESC";
        return jdbcTemplate.query(sql, new SkillRowMapper());
    }

    public SkillRecord findById(String id) {
        String sql = "SELECT * FROM skills WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, new SkillRowMapper(), id);
    }

    public void save(SkillRecord skill) {
        String sql = """
                INSERT INTO skills (id, name, description, version, source,content, enabled,
                                    associated_agents, parameters, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?,?, ?, ?)
                """;
        jdbcTemplate.update(sql,
                skill.getId(),
                skill.getName(),
                skill.getDescription(),
                skill.getVersion(),
                skill.getSource(),
                skill.getContent(),
                skill.isEnabled() ? 1 : 0,
                skill.getAssociatedAgents(),
                skill.getParameters(),
                skill.getCreatedAt(),
                skill.getUpdatedAt());
    }

    public void update(SkillRecord skill) {
        String sql = """
                UPDATE skills SET name = ?, description = ?, version = ?, source = ?, content = ?,enabled = ?,
                                  associated_agents = ?, parameters = ?, updated_at = ?
                WHERE id = ?
                """;
        jdbcTemplate.update(sql,
                skill.getName(),
                skill.getDescription(),
                skill.getVersion(),
                skill.getSource(),
                skill.getContent(),
                skill.isEnabled() ? 1 : 0,
                skill.getAssociatedAgents(),
                skill.getParameters(),
                skill.getUpdatedAt(),
                skill.getId());
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM skills WHERE id = ?", id);
    }

    public void toggleEnabled(String id, boolean enabled) {
        jdbcTemplate.update("UPDATE skills SET enabled = ?, updated_at = ? WHERE id = ?",
                enabled ? 1 : 0, System.currentTimeMillis(), id);
    }

    private static class SkillRowMapper implements RowMapper<SkillRecord> {
        @Override
        public SkillRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return SkillRecord.builder()
                    .id(rs.getString("id"))
                    .name(rs.getString("name"))
                    .description(rs.getString("description"))
                    .version(rs.getString("version"))
                    .source(rs.getString("source"))
                    .content(rs.getString("content"))
                    .enabled(rs.getInt("enabled") == 1)
                    .associatedAgents(rs.getString("associated_agents"))
                    .parameters(rs.getString("parameters"))
                    .createdAt(rs.getLong("created_at"))
                    .updatedAt(rs.getLong("updated_at"))
                    .build();
        }
    }
}