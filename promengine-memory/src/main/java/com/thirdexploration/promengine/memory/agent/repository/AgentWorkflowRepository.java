package com.thirdexploration.promengine.memory.agent.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.memory.agent.model.AgentWorkflow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class AgentWorkflowRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentWorkflowRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public AgentWorkflow findById(String id) {
        return jdbcTemplate.queryForObject(
            "SELECT * FROM agent_workflows WHERE id = ?",
            new WorkflowRowMapper(), id);
    }

    public List<AgentWorkflow> findAll() {
        return jdbcTemplate.query("SELECT * FROM agent_workflows", new WorkflowRowMapper());
    }

    public void save(AgentWorkflow wf) {
        jdbcTemplate.update(
            "INSERT INTO agent_workflows (id, name, description, version, steps, triggers, max_steps, timeout_seconds, fallback_strategy, created_by, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            wf.getId(), wf.getName(), wf.getDescription(), wf.getVersion(),
            wf.getSteps(), wf.getTriggers(), wf.getMaxSteps(), wf.getTimeoutSeconds(),
            wf.getFallbackStrategy(), wf.getCreatedBy(), wf.getCreatedAt(), wf.getUpdatedAt());
    }

    public void update(AgentWorkflow wf) {
        jdbcTemplate.update(
            "UPDATE agent_workflows SET name=?, description=?, version=?, steps=?, triggers=?, max_steps=?, timeout_seconds=?, fallback_strategy=?, updated_at=? WHERE id=?",
            wf.getName(), wf.getDescription(), wf.getVersion(), wf.getSteps(),
            wf.getTriggers(), wf.getMaxSteps(), wf.getTimeoutSeconds(),
            wf.getFallbackStrategy(), wf.getUpdatedAt(), wf.getId());
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM agent_workflows WHERE id = ?", id);
    }

    private class WorkflowRowMapper implements RowMapper<AgentWorkflow> {
        @Override
        public AgentWorkflow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AgentWorkflow.builder()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .version(rs.getString("version"))
                .steps(rs.getString("steps"))
                .triggers(rs.getString("triggers"))
                .maxSteps(rs.getInt("max_steps"))
                .timeoutSeconds(rs.getInt("timeout_seconds"))
                .fallbackStrategy(rs.getString("fallback_strategy"))
                .createdBy(rs.getString("created_by"))
                .createdAt(rs.getLong("created_at"))
                .updatedAt(rs.getLong("updated_at"))
                .build();
        }
    }
}