package com.thirdexploration.promengine.memory.agent.repository;

import com.thirdexploration.promengine.memory.agent.model.ReActStepRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class ReActStepRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReActStepRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(ReActStepRecord record) {
        String sql = """
            INSERT INTO react_step_events 
            (id, agent_id, session_id, step_number, type, description, detail, status,execution_id, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ? ,?)
            """;
        jdbcTemplate.update(sql,
                record.getId(),
                record.getAgentId(),
                record.getSessionId(),
                record.getStepNumber(),
                record.getType(),
                record.getDescription(),
                record.getDetail(),
                record.getStatus(),
                record.getExecutionId(),
                record.getTimestamp());
    }

    public List<ReActStepRecord> findBySessionId(String sessionId) {
        String sql = "SELECT * FROM react_step_events WHERE session_id = ? ORDER BY timestamp ASC";
        return jdbcTemplate.query(sql, new StepRowMapper(), sessionId);
    }
    public List<ReActStepRecord> findByExecutionId(String executionId) {
        String sql = "SELECT * FROM react_step_events WHERE execution_id = ? ORDER BY step_number ASC";
        return jdbcTemplate.query(sql, new StepRowMapper(), executionId);
    }
    private static class StepRowMapper implements RowMapper<ReActStepRecord> {
        @Override
        public ReActStepRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return ReActStepRecord.builder()
                    .id(rs.getString("id"))
                    .agentId(rs.getString("agent_id"))
                    .sessionId(rs.getString("session_id"))
                    .stepNumber(rs.getInt("step_number"))
                    .type(rs.getString("type"))
                    .description(rs.getString("description"))
                    .detail(rs.getString("detail"))
                    .status(rs.getString("status"))
                    .executionId(rs.getString("execution_id"))
                    .timestamp(rs.getLong("timestamp"))
                    .build();
        }
    }
}