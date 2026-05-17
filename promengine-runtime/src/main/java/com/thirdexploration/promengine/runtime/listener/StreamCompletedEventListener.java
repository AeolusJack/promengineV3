package com.thirdexploration.promengine.runtime.listener;

import com.thirdexploration.promengine.executor.event.StreamCompletedEvent;
import com.thirdexploration.promengine.runtime.model.ChatMessage;
import com.thirdexploration.promengine.runtime.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamCompletedEventListener {

    private final ChatMessageRepository chatMessageRepository;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void handleStreamCompleted(StreamCompletedEvent event) {
        log.info("Received StreamCompletedEvent for executionId: {}", event.getExecutionId());
        // 重试3次，每次间隔500ms
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                // 检查是否已存在
                if ( 1 < chatMessageRepository.countByExecutionId(event.getExecutionId())) {
                    log.info("Assistant already exists, skip. executionId={}", event.getExecutionId());
                    return;
                }
                ChatMessage assistantMsg = ChatMessage.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(event.getUserId())
                        .sessionId(event.getSessionId())
                        .executionId(event.getExecutionId())
                        .role("assistant")
                        .content(event.getFinalAnswer())
                        .timestamp(System.currentTimeMillis())
                        .createdAt(System.currentTimeMillis())
                        .sessionName("")
                        .build();
                chatMessageRepository.save(assistantMsg);
                log.info("Assistant message saved for executionId: {}", event.getExecutionId());
                return; // 成功则退出
            } catch (Exception e) {
                log.warn("Failed to save assistant message (attempt {}/3): {}", attempt, e.getMessage());
                if (attempt == 3) {
                    log.error("Failed to save assistant message after 3 attempts", e);
                    // 可选：将失败记录到死信表
                }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
    }
}