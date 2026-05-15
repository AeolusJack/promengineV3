package com.thirdexploration.promengine.memory.agent.repository;

import com.thirdexploration.promengine.memory.agent.model.AgentExecutionLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class AgentExecutionLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentExecutionLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(AgentExecutionLog log) {
        String sql = """
            INSERT INTO agent_execution_logs 
            (id, agent_id, session_id, task_id, step_name, status, input, output, 
             error_message, start_time, end_time, duration_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql,
                log.getId(),
                log.getAgentId(),
                log.getSessionId(),
                log.getTaskId(),
                log.getStepName(),
                log.getStatus(),
                log.getInput(),
                log.getOutput(),
                log.getErrorMessage(),
                log.getStartTime(),
                log.getEndTime(),
                log.getDurationMs(),
                log.getCreatedAt());
    }

    public void updateStatus(String id, String status, Long endTime, Long durationMs, String errorMessage) {
        String sql = "UPDATE agent_execution_logs SET status = ?, end_time = ?, duration_ms = ?, error_message = ? WHERE id = ?";
        jdbcTemplate.update(sql, status, endTime, durationMs, errorMessage, id);
    }

    public List<AgentExecutionLog> findByAgentId(String agentId, int limit) {
        String sql = "SELECT * FROM agent_execution_logs WHERE agent_id = ? ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, new ExecutionLogRowMapper(), agentId, limit);
    }

    public List<AgentExecutionLog> findBySessionId(String sessionId) {
        String sql = "SELECT * FROM agent_execution_logs WHERE session_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, new ExecutionLogRowMapper(), sessionId);
    }

    private static class ExecutionLogRowMapper implements RowMapper<AgentExecutionLog> {
        @Override
        public AgentExecutionLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AgentExecutionLog.builder()
                    .id(rs.getString("id"))
                    .agentId(rs.getString("agent_id"))
                    .sessionId(rs.getString("session_id"))
                    .taskId(rs.getString("task_id"))
                    .stepName(rs.getString("step_name"))
                    .status(rs.getString("status"))
                    .input(rs.getString("input"))
                    .output(rs.getString("output"))
                    .errorMessage(rs.getString("error_message"))
                    .startTime(rs.getLong("start_time"))
                    .endTime(rs.getObject("end_time", Long.class))
                    .durationMs(rs.getObject("duration_ms", Long.class))
                    .createdAt(rs.getLong("created_at"))
                    .build();
        }
    }
}