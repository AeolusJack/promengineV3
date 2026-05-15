package com.thirdexploration.promengine.memory.agent.repository;

import com.thirdexploration.promengine.memory.agent.model.AgentTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class AgentTemplateRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentTemplateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(AgentTemplate template) {
        String sql = """
            INSERT INTO agent_templates 
            (id, name, category, description, template_config, created_by, visibility, downloads, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                template.getId(),
                template.getName(),
                template.getCategory(),
                template.getDescription(),
                template.getTemplateConfig(),
                template.getCreatedBy(),
                template.getVisibility(),
                template.getDownloads(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }

    public AgentTemplate findById(String id) {
        String sql = "SELECT * FROM agent_templates WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, new TemplateRowMapper(), id);
    }

    public List<AgentTemplate> findByCategory(String category, int limit, int offset) {
        String sql = "SELECT * FROM agent_templates WHERE category = ? AND visibility = 'public' ORDER BY downloads DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, new TemplateRowMapper(), category, limit, offset);
    }

    public List<AgentTemplate> findByCreator(String createdBy) {
        String sql = "SELECT * FROM agent_templates WHERE created_by = ? ORDER BY updated_at DESC";
        return jdbcTemplate.query(sql, new TemplateRowMapper(), createdBy);
    }

    public void incrementDownloads(String id) {
        jdbcTemplate.update("UPDATE agent_templates SET downloads = downloads + 1 WHERE id = ?", id);
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM agent_templates WHERE id = ?", id);
    }

    private static class TemplateRowMapper implements RowMapper<AgentTemplate> {
        @Override
        public AgentTemplate mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AgentTemplate.builder()
                    .id(rs.getString("id"))
                    .name(rs.getString("name"))
                    .category(rs.getString("category"))
                    .description(rs.getString("description"))
                    .templateConfig(rs.getString("template_config"))
                    .createdBy(rs.getString("created_by"))
                    .visibility(rs.getString("visibility"))
                    .downloads(rs.getInt("downloads"))
                    .createdAt(rs.getLong("created_at"))
                    .updatedAt(rs.getLong("updated_at"))
                    .build();
        }
    }
}