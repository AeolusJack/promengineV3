package com.thirdexploration.promengine.memory.agent.repository;

import com.thirdexploration.promengine.memory.agent.model.AgentKnowledgeBase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class AgentKnowledgeBaseRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentKnowledgeBaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AgentKnowledgeBase> findByAgentId(String agentId) {
        String sql = "SELECT * FROM agent_knowledge_bases WHERE agent_id = ? AND enabled = 1 ORDER BY priority DESC";
        return jdbcTemplate.query(sql, new KnowledgeBaseRowMapper(), agentId);
    }

    public AgentKnowledgeBase findById(String id) {
        String sql = "SELECT * FROM agent_knowledge_bases WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, new KnowledgeBaseRowMapper(), id);
    }

    public void save(AgentKnowledgeBase kb) {
        String sql = """
            INSERT INTO agent_knowledge_bases 
            (id, agent_id, name, type, config, priority, enabled, created_at, updated_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                kb.getId(),
                kb.getAgentId(),
                kb.getName(),
                kb.getType(),
                kb.getConfig(),
                kb.getPriority(),
                kb.isEnabled() ? 1 : 0,
                kb.getCreatedAt(),
                kb.getUpdatedAt());
    }

    public void update(AgentKnowledgeBase kb) {
        String sql = """
            UPDATE agent_knowledge_bases 
            SET name = ?, type = ?, config = ?, priority = ?, enabled = ?, updated_at = ? 
            WHERE id = ?
            """;
        jdbcTemplate.update(sql,
                kb.getName(),
                kb.getType(),
                kb.getConfig(),
                kb.getPriority(),
                kb.isEnabled() ? 1 : 0,
                kb.getUpdatedAt(),
                kb.getId());
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM agent_knowledge_bases WHERE id = ?", id);
    }

    private static class KnowledgeBaseRowMapper implements RowMapper<AgentKnowledgeBase> {
        @Override
        public AgentKnowledgeBase mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AgentKnowledgeBase.builder()
                    .id(rs.getString("id"))
                    .agentId(rs.getString("agent_id"))
                    .name(rs.getString("name"))
                    .type(rs.getString("type"))
                    .config(rs.getString("config"))
                    .priority(rs.getInt("priority"))
                    .enabled(rs.getInt("enabled") == 1)
                    .createdAt(rs.getLong("created_at"))
                    .updatedAt(rs.getLong("updated_at"))
                    .build();
        }
    }
}