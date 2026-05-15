package com.thirdexploration.promengine.memory.agent.repository;

import com.thirdexploration.promengine.memory.agent.model.AgentEvaluation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class AgentEvaluationRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentEvaluationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(AgentEvaluation eval) {
        String sql = """
            INSERT INTO agent_evaluations 
            (id, agent_id, session_id, rating, tags, comment, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                eval.getId(),
                eval.getAgentId(),
                eval.getSessionId(),
                eval.getRating(),
                eval.getTags(),
                eval.getComment(),
                eval.getCreatedAt());
    }

    public List<AgentEvaluation> findByAgentId(String agentId, int limit) {
        String sql = "SELECT * FROM agent_evaluations WHERE agent_id = ? ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, new EvaluationRowMapper(), agentId, limit);
    }

    public Double getAverageRating(String agentId) {
        String sql = "SELECT AVG(rating) FROM agent_evaluations WHERE agent_id = ?";
        return jdbcTemplate.queryForObject(sql, Double.class, agentId);
    }

    public int countByAgentId(String agentId) {
        String sql = "SELECT COUNT(*) FROM agent_evaluations WHERE agent_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, agentId);
        return count != null ? count : 0;
    }

    private static class EvaluationRowMapper implements RowMapper<AgentEvaluation> {
        @Override
        public AgentEvaluation mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AgentEvaluation.builder()
                    .id(rs.getString("id"))
                    .agentId(rs.getString("agent_id"))
                    .sessionId(rs.getString("session_id"))
                    .rating(rs.getInt("rating"))
                    .tags(rs.getString("tags"))
                    .comment(rs.getString("comment"))
                    .createdAt(rs.getLong("created_at"))
                    .build();
        }
    }
}