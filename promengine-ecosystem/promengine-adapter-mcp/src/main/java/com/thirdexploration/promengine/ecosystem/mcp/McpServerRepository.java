package com.thirdexploration.promengine.ecosystem.mcp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class McpServerRepository {

    private final JdbcTemplate jdbcTemplate;

    public McpServerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<McpServerRecord> findAll() {
        String sql = "SELECT * FROM mcp_servers ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new McpServerRowMapper());
    }

    public McpServerRecord findById(String id) {
        String sql = "SELECT * FROM mcp_servers WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, new McpServerRowMapper(), id);
    }

    public void save(McpServerRecord record) {
        String sql = "INSERT INTO mcp_servers (id, name, url, enabled, created_at) VALUES (?,?, ?, ?, ?)";
        jdbcTemplate.update(sql, record.getId(), record.getName(), record.getUrl(),
                record.isEnabled() ? 1 : 0, record.getCreatedAt());
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM mcp_servers WHERE id = ?", id);
    }

    public void updateEnabled(String id, boolean enabled) {
        jdbcTemplate.update("UPDATE mcp_servers SET enabled = ? WHERE id = ?", enabled ? 1 : 0, id);
    }

    private static class McpServerRowMapper implements RowMapper<McpServerRecord> {
        @Override
        public McpServerRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return McpServerRecord.builder()
                    .id(rs.getString("id"))
                    .name(rs.getString("name"))
                    .url(rs.getString("url"))
                    .enabled(rs.getInt("enabled") == 1)
                    .createdAt(rs.getLong("created_at"))
                    .build();
        }
    }
}