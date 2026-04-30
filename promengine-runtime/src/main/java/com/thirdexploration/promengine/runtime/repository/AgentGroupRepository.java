package com.thirdexploration.promengine.runtime.repository;

import com.thirdexploration.promengine.runtime.model.AgentGroup;
import com.thirdexploration.promengine.runtime.model.GroupAgent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class AgentGroupRepository {
    private final JdbcTemplate jdbcTemplate;

    public AgentGroupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AgentGroup> findAllByUser(String userId) {
        String sql = "SELECT * FROM agent_groups WHERE user_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new GroupRowMapper(), userId);
    }

    public AgentGroup findById(String id) {
        AgentGroup group = jdbcTemplate.queryForObject(
                "SELECT * FROM agent_groups WHERE id = ?", new GroupRowMapper(), id);
        if (group != null) {
            group.setAgents(findAgentsByGroup(id));
        }
        return group;
    }

    public void save(AgentGroup group) {
        jdbcTemplate.update(
                "INSERT INTO agent_groups (id, user_id, name, topic, max_rounds, auto_mode, status, created_at) VALUES (?,?,?,?,?,?,?,?)",
                group.getId(), group.getUserId(), group.getName(), group.getTopic(),
                group.getMaxRounds(), group.isAutoMode() ? 1 : 0, group.getStatus(),
                group.getCreatedAt());
    }

    public void update(AgentGroup group) {
        jdbcTemplate.update(
                "UPDATE agent_groups SET user_id=?, name=?, topic=?, max_rounds=?, auto_mode=?, status=? WHERE id=?",
                group.getUserId(),
                group.getName(),
                group.getTopic(),
                group.getMaxRounds(),
                group.isAutoMode() ? 1 : 0,
                group.getStatus(),
                group.getId()
        );
    }


    public void delete(String id) {
        jdbcTemplate.update("DELETE FROM group_agents WHERE group_id = ?", id);
        jdbcTemplate.update("DELETE FROM agent_groups WHERE id = ?", id);
    }

    public void addAgentToGroup(String groupId, GroupAgent agent) {
        jdbcTemplate.update(
                "INSERT INTO group_agents (group_id, agent_id, role) VALUES (?,?,?)",
                groupId, agent.getAgentId(), agent.getRole());
    }

    public List<GroupAgent> findAgentsByGroup(String groupId) {
        String sql = """
            SELECT ga.agent_id, a.name, a.avatar, ga.role
            FROM group_agents ga
            JOIN agents a ON ga.agent_id = a.id
            WHERE ga.group_id = ?
            """;
        return jdbcTemplate.query(sql, (rs, rn) -> GroupAgent.builder()
                .agentId(rs.getString("agent_id"))
                .name(rs.getString("name"))
                .avatar(rs.getString("avatar"))
                .role(rs.getString("role"))
                .build(), groupId);
    }

    private class GroupRowMapper implements RowMapper<AgentGroup> {
        @Override
        public AgentGroup mapRow(ResultSet rs, int rowNum) throws SQLException {
            return AgentGroup.builder()
                    .id(rs.getString("id"))
                    .userId(rs.getString("user_id"))
                    .name(rs.getString("name"))
                    .topic(rs.getString("topic"))
                    .maxRounds(rs.getInt("max_rounds"))
                    .autoMode(rs.getInt("auto_mode") == 1)
                    .status(rs.getString("status"))
                    .createdAt(rs.getLong("created_at"))
                    .build();
        }
    }
}