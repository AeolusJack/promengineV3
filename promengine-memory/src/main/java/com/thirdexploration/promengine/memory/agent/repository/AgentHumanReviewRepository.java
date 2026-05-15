package com.thirdexploration.promengine.memory.agent.repository;

import com.thirdexploration.promengine.memory.agent.model.AgentHumanReview;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class AgentHumanReviewRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentHumanReviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(AgentHumanReview review) {
        String sql = """
            INSERT INTO agent_human_reviews 
            (id, agent_id, session_id, task_id, request_type, request_data, response_data, status, created_at, resolved_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                review.getId(),
                review.getAgentId(),
                review.getSessionId(),
                review.getTaskId(),
                review.getRequestType(),
                review.getRequestData(),
                review.getResponseData(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getResolvedAt());
    }

    public void updateDecision(String id, String status, String responseData, long resolvedAt) {
        String sql = "UPDATE agent_human_reviews SET status = ?, response_data = ?, resolved_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, status, responseData, resolvedAt, id);
    }

    public List<AgentHumanReview> findByAgentIdAndStatus(String agentId, String status, int limit) {
        String sql = "SELECT * FROM agent_human_reviews WHERE agent_id = ? AND status = ? ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, new ReviewRowMapper(), agentId, status, limit);
    }

    public AgentHumanReview findById(String id) {
        String sql = "SELECT * FROM agent_human_reviews WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, new ReviewRowMapper(), id);
    }

    private static class ReviewRowMapper implements RowMapper<AgentHumanReview> {
        @Override
        public AgentHumanReview mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AgentHumanReview.builder()
                    .id(rs.getString("id"))
                    .agentId(rs.getString("agent_id"))
                    .sessionId(rs.getString("session_id"))
                    .taskId(rs.getString("task_id"))
                    .requestType(rs.getString("request_type"))
                    .requestData(rs.getString("request_data"))
                    .responseData(rs.getString("response_data"))
                    .status(rs.getString("status"))
                    .createdAt(rs.getLong("created_at"))
                    .resolvedAt(rs.getObject("resolved_at", Long.class))
                    .build();
        }
    }
}