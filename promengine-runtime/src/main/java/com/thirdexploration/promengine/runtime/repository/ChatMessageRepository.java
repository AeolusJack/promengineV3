package com.thirdexploration.promengine.runtime.repository;

import com.thirdexploration.promengine.runtime.model.ChatMessage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class ChatMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    public int countByExecutionId(String executionId) {
        String sql = "SELECT COUNT(*) FROM chat_messages WHERE execution_id = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, executionId);
    }
    /**
     * 保存消息，使用 INSERT OR IGNORE
     * @return true 如果插入成功，false 如果已存在
     */
    public boolean save(ChatMessage message) {
        String sql = """
                INSERT OR IGNORE INTO chat_messages 
                (id, user_id, session_id, session_name, role, content, execution_id, timestamp, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        int rows = jdbcTemplate.update(sql,
                message.getId(),
                message.getUserId(),
                message.getSessionId(),
                message.getSessionName(),
                message.getRole(),
                message.getContent(),
                message.getExecutionId(),
                message.getTimestamp(),
                message.getCreatedAt());
        return rows > 0;
    }

    /**
     * 检查指定 executionId 的消息是否已存在
     */
    public boolean existsByExecutionId(String executionId) {
        String sql = "SELECT COUNT(*) FROM chat_messages WHERE execution_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, executionId);
        return count != null && count > 0;
    }

    // 其余方法不变...
    public List<ChatMessage> findBySessionId(String userId, String sessionId) {
        String sql = """
                SELECT * FROM chat_messages
                WHERE user_id = ? AND session_id = ?
                ORDER BY timestamp ASC
                """;
        return jdbcTemplate.query(sql, new ChatMessageRowMapper(), userId, sessionId);
    }

    public List<ChatMessage> findBySessionIdAndRole(String role, String sessionId) {
        String sql = """
                SELECT * FROM chat_messages
                WHERE role = ? AND session_id = ?
                ORDER BY timestamp ASC
                """;
        return jdbcTemplate.query(sql, new ChatMessageRowMapper(), role, sessionId);
    }

    public List<ChatMessage> findBySessionId(String sessionId) {
        String sql = """
                SELECT * FROM chat_messages
                WHERE session_id = ?
                ORDER BY timestamp ASC
                """;
        return jdbcTemplate.query(sql, new ChatMessageRowMapper(), sessionId);
    }

    public boolean isFirstMessage(String userId, String sessionId) {
        String sql = "SELECT COUNT(*) FROM chat_messages WHERE user_id = ? AND session_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, sessionId);
        return count == null || count == 0;
    }

    public void updateSessionName(String userId, String sessionId, String newName) {
        String sql = "UPDATE chat_messages SET session_name = ? WHERE user_id = ? AND session_id = ? AND id = (SELECT id FROM chat_messages WHERE user_id = ? AND session_id = ? ORDER BY timestamp ASC LIMIT 1)";
        jdbcTemplate.update(sql, newName, userId, sessionId, userId, sessionId);
    }

    public List<SessionInfo> findDistinctSessions(String userId) {
        String sql = """
                SELECT session_id,
                       COALESCE(
                           (SELECT session_name FROM chat_messages WHERE session_id = c.session_id AND session_name IS NOT NULL ORDER BY timestamp ASC LIMIT 1),
                           '会话' || substr(c.session_id, 1, 8)
                       ) AS session_name
                FROM chat_messages c
                WHERE c.user_id = ?
                GROUP BY c.session_id
                ORDER BY MAX(c.timestamp) DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SessionInfo(rs.getString("session_id"), rs.getString("session_name")), userId);
    }

    private static class ChatMessageRowMapper implements RowMapper<ChatMessage> {
        @Override
        public ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            return ChatMessage.builder()
                    .id(rs.getString("id"))
                    .userId(rs.getString("user_id"))
                    .sessionId(rs.getString("session_id"))
                    .sessionName(rs.getString("session_name"))
                    .role(rs.getString("role"))
                    .content(rs.getString("content"))
                    .timestamp(rs.getLong("timestamp"))
                    .createdAt(rs.getLong("created_at"))
                    .executionId(rs.getString("execution_id"))
                    .build();
        }
    }

    public record SessionInfo(String id, String name) {}
}