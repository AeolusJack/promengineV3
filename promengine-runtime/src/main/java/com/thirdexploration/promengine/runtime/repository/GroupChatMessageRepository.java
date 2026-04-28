package com.thirdexploration.promengine.runtime.repository;

import com.thirdexploration.promengine.runtime.model.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class GroupChatMessageRepository extends ChatMessageRepository {

    public GroupChatMessageRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public ChatMessage saveGroupMessage(String groupId, String role, String agentId,
                                        String agentName, String avatar, String content) {
        ChatMessage msg = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .userId(agentId)      // 群组消息不属于某个特定用户
                .sessionId(groupId)    // 复用 session_id
                .sessionName(role+"_"+agentName)
                .role(role)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .createdAt(System.currentTimeMillis())
                .build();
        save(msg);
        return msg;
    }

    public List<ChatMessage> findByGroupId(String groupId) {
        return findBySessionIdAndRole("agent", groupId);
    }
}