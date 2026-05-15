package com.thirdexploration.promengine.memory.agent.repository;

import com.thirdexploration.promengine.memory.agent.model.AgentToolBinding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class AgentToolBindingRepository {
    private final JdbcTemplate jdbcTemplate;

    public AgentToolBindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AgentToolBinding> findByAgentId(String agentId) {
        return jdbcTemplate.query(
            "SELECT * FROM agent_tool_bindings WHERE agent_id = ? AND enabled = 1",
            new ToolBindingRowMapper(), agentId);
    }

    public void save(AgentToolBinding binding) {
        jdbcTemplate.update(
            "INSERT INTO agent_tool_bindings (id, agent_id, tool_name, config, enabled, created_at) VALUES (?,?,?,?,?,?)",
            binding.getId(), binding.getAgentId(), binding.getToolName(),
            binding.getConfig(), binding.isEnabled() ? 1 : 0, binding.getCreatedAt());
    }

    public void deleteByAgentId(String agentId) {
        jdbcTemplate.update("DELETE FROM agent_tool_bindings WHERE agent_id = ?", agentId);
    }

    public void toggleEnabled(String id, boolean enabled) {
        jdbcTemplate.update("UPDATE agent_tool_bindings SET enabled = ? WHERE id = ?",
                enabled ? 1 : 0, id);
    }

    private static class ToolBindingRowMapper implements RowMapper<AgentToolBinding> {
        @Override
        public AgentToolBinding mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AgentToolBinding.builder()
                .id(rs.getString("id"))
                .agentId(rs.getString("agent_id"))
                .toolName(rs.getString("tool_name"))
                .config(rs.getString("config"))
                .enabled(rs.getInt("enabled") == 1)
                .createdAt(rs.getLong("created_at"))
                .build();
        }
    }
}