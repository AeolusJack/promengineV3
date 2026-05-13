package com.thirdexploration.promengine.ecosystem.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
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

    // McpServerRepository.java
    public McpServerRecord findById(String id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM mcp_servers WHERE id = ?",
                    new McpServerRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

//    public void save(McpServerRecord record) {
//        String sql = "INSERT INTO mcp_servers (id, name, url, enabled, created_at) VALUES (?,?, ?, ?, ?)";
//        jdbcTemplate.update(sql, record.getId(), record.getName(), record.getUrl(),
//                record.isEnabled() ? 1 : 0, record.getCreatedAt());
//    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM mcp_servers WHERE id = ?", id);
    }

    public void updateEnabled(String id, boolean enabled) {
        jdbcTemplate.update("UPDATE mcp_servers SET enabled = ? WHERE id = ?", enabled ? 1 : 0, id);
    }

//    private static class McpServerRowMapper implements RowMapper<McpServerRecord> {
//        @Override
//        public McpServerRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
//            return McpServerRecord.builder()
//                    .id(rs.getString("id"))
//                    .name(rs.getString("name"))
//                    .url(rs.getString("url"))
//                    .enabled(rs.getInt("enabled") == 1)
//                    .createdAt(rs.getLong("created_at"))
//                    .build();
//        }
//    }



    // McpServerRepository.java (save 方法)
    public void save(McpServerRecord record) {
        String sql = "INSERT INTO mcp_servers (id, name, url, enabled, created_at, auth_token, transport, command, args, headers) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql,
                    record.getId(),
                    record.getName(),
                    record.getUrl(),
                    record.isEnabled() ? 1 : 0,
                    record.getCreatedAt(),
                    record.getAuthToken(),
                    record.getTransport(),
                    record.getCommand(),
                    record.getArgs(),
                    new ObjectMapper().writeValueAsString(record.getHeaders())
            );
        } catch (JsonProcessingException e) {
            log.warn("save  McpServerRecord fail",e);
            throw new RuntimeException(e);
        }
    }

    // RowMapper 中读取新字段
    private static class McpServerRowMapper implements RowMapper<McpServerRecord> {
        @Override
        public McpServerRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return McpServerRecord.builder()
                    .id(rs.getString("id"))
                    .name(rs.getString("name"))
                    .url(rs.getString("url"))
                    .enabled(rs.getInt("enabled") == 1)
                    .createdAt(rs.getLong("created_at"))
                    .authToken(rs.getString("auth_token"))
                    .transport(rs.getString("transport"))
                    .command(rs.getString("command"))
                    .args(rs.getString("args"))
                    .headers(parseHeaders(rs.getString("headers")))
                    .build();
        }
    }

    private static Map<String, String> parseHeaders(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return new ObjectMapper().readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

//    private static Map<String, String> parseHeaders(String headersJson) {
//        if (headersJson == null || headersJson.isBlank()) return Map.of();
//        try {
//            return new ObjectMapper().readValue(headersJson, new TypeReference<Map<String, String>>() {});
//        } catch (Exception e) { return Map.of(); }
//    }

}