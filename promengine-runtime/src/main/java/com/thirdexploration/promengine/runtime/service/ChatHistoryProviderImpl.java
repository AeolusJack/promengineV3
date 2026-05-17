package com.thirdexploration.promengine.runtime.service;

import com.thirdexploration.promengine.core.agent.ChatHistoryProvider;
import com.thirdexploration.promengine.runtime.model.ChatMessage;
import com.thirdexploration.promengine.runtime.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ChatHistoryProviderImpl implements ChatHistoryProvider {

    private final ChatMessageRepository chatMessageRepository;

    @Override
    public List<HistoryMessage> getRecentHistory(String sessionId, int maxMessages) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        List<ChatMessage> allMessages = chatMessageRepository.findBySessionId(sessionId);
        // 过滤掉可能的 system 角色，只保留 user 和 assistant
        List<ChatMessage> history = allMessages.stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .collect(Collectors.toList());

        // 取最近的 maxMessages 条（从尾部取）
        if (history.size() > maxMessages) {
            history = history.subList(history.size() - maxMessages, history.size());
        }

        return history.stream()
                .map(m -> HistoryMessage.of(m.getRole(), m.getContent()))
                .collect(Collectors.toList());
    }
}