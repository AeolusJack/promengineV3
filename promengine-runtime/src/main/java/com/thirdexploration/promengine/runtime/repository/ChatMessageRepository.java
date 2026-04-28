package com.thirdexploration.promengine.runtime.repository;

import com.thirdexploration.promengine.runtime.model.ChatMessage;
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

    public void save(ChatMessage message) {
        String sql = """
                INSERT INTO chat_messages (id, user_id, session_id, role, content, timestamp, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql,
                message.getId(),
                message.getUserId(),
                message.getSessionId(),
                message.getRole(),
                message.getContent(),
                message.getTimestamp(),
                message.getCreatedAt());
    }

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

//    /**
//     * 获取指定用户的所有会话 ID（降序，最近活跃在前）
//     */
//    public List<String> findDistinctSessions(String userId) {
//        String sql = """
//                SELECT session_id FROM chat_messages
//                WHERE user_id = ?
//                GROUP BY session_id
//                ORDER BY MAX(timestamp) DESC
//                """;
//        return jdbcTemplate.queryForList(sql, String.class, userId);
//    }

    private static class ChatMessageRowMapper implements RowMapper<ChatMessage> {
        @Override
        public ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            return ChatMessage.builder()
                    .id(rs.getString("id"))
                    .userId(rs.getString("user_id"))
                    .sessionId(rs.getString("session_id"))
                    .role(rs.getString("role"))
                    .content(rs.getString("content"))
                    .timestamp(rs.getLong("timestamp"))
                    .createdAt(rs.getLong("created_at"))
                    .build();
        }
    }


    // 判断某会话是否已有消息
    public boolean isFirstMessage(String userId, String sessionId) {
        String sql = "SELECT COUNT(*) FROM chat_messages WHERE user_id = ? AND session_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, sessionId);
        return count == null || count == 0;
    }

    // 更新会话名称（将所有属于该会话且 role='user' 的第一条消息的 session_name 更新即可）
    public void updateSessionName(String userId, String sessionId, String newName) {
        String sql = "UPDATE chat_messages SET session_name = ? WHERE user_id = ? AND session_id = ? AND id = (SELECT id FROM chat_messages WHERE user_id = ? AND session_id = ? ORDER BY timestamp ASC LIMIT 1)";
        jdbcTemplate.update(sql, newName, userId, sessionId, userId, sessionId);
    }

    // 返回会话列表（含名称）
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

    // SessionInfo 内部类
    public record SessionInfo(String id, String name) {}
}