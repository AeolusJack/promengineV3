package com.thirdexploration.promengine.runtime.service;

import com.thirdexploration.promengine.core.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class TeamService {
    private final JdbcTemplate jdbcTemplate;

    public TeamService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Map<String, Object> createTeam(Map<String, Object> body, String userId) {
        String name = (String) body.get("name");
        String teamId = UUID.randomUUID().toString();
        String tenantId = TenantContext.getOrDefault();
        jdbcTemplate.update(
            "INSERT INTO teams (id, tenant_id, name, description, owner_id, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
            teamId, tenantId, name, body.getOrDefault("description", ""), userId, System.currentTimeMillis(), System.currentTimeMillis()
        );
        // 自动将创建者加入为 owner
        jdbcTemplate.update(
            "INSERT INTO team_members (id, team_id, user_id, role, joined_at) VALUES (?,?,?,?,?)",
            UUID.randomUUID().toString(), teamId, userId, "owner", System.currentTimeMillis()
        );
        return Map.of("id", teamId, "name", name);
    }

    public List<Map<String, Object>> listUserTeams(String userId) {
        String tenantId = TenantContext.getOrDefault();
        return jdbcTemplate.queryForList(
            "SELECT * FROM teams WHERE tenant_id = ? AND id IN (SELECT team_id FROM team_members WHERE user_id = ?)",
            tenantId, userId
        );
    }

    public void addMember(String teamId, String userId, String role) {
        // 检查是否已存在
        // 插入 team_members
        jdbcTemplate.update(
            "INSERT OR IGNORE INTO team_members (id, team_id, user_id, role, joined_at) VALUES (?,?,?,?,?)",
            UUID.randomUUID().toString(), teamId, userId, role, System.currentTimeMillis()
        );
    }

    public void removeMember(String teamId, String userId) {
        jdbcTemplate.update("DELETE FROM team_members WHERE team_id = ? AND user_id = ?", teamId, userId);
    }

    public List<Map<String, Object>> getMembers(String teamId) {
        return jdbcTemplate.queryForList(
            "SELECT u.id, u.username, u.nickname, u.avatar, tm.role FROM team_members tm JOIN users u ON tm.user_id = u.id WHERE tm.team_id = ?",
            teamId
        );
    }
}