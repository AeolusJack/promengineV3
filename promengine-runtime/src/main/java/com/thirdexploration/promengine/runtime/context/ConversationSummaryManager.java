package com.thirdexploration.promengine.runtime.context;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thirdexploration.promengine.core.agent.ChatHistoryProvider;
import com.thirdexploration.promengine.core.context.ConversationContext;
import com.thirdexploration.promengine.core.context.ConversationContextBuilder;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationSummaryManager implements ConversationContextBuilder {

    private final ChatHistoryProvider chatHistoryProvider;
    private final UnifiedMemoryAPI memoryAPI;
    private final ChatClient.Builder chatClientBuilder;

    // 摘要缓存：sessionId -> 最新摘要
    private final Cache<String, String> summaryCache = Caffeine.newBuilder()
            .maximumSize(5_000)
            .expireAfterAccess(Duration.ofHours(6))
            .build();

    @Override
    public ConversationContext buildContext(String sessionId, int windowSize) {
        if (sessionId == null || sessionId.isBlank()) {
            return ConversationContext.builder().build();
        }

        // 1. 获取所有历史消息（最多取 500 条，避免全量）
        List<ChatHistoryProvider.HistoryMessage> allHistory = chatHistoryProvider.getRecentHistory(sessionId, 500);
        if (allHistory.isEmpty()) {
            return ConversationContext.builder().build();
        }

        // 2. 分割窗口
        int splitIndex = Math.max(0, allHistory.size() - windowSize);
        List<ChatHistoryProvider.HistoryMessage> windowMessages = allHistory.subList(splitIndex, allHistory.size());
        List<ChatHistoryProvider.HistoryMessage> oldMessages = allHistory.subList(0, splitIndex);

        // 3. 获取或生成摘要
        String summary = "";
        if (!oldMessages.isEmpty()) {
            summary = summaryCache.get(sessionId, key -> generateSummary(oldMessages));
        }

        // 4. 格式化最近窗口为文本
        String recentHistoryText = formatMessages(windowMessages);

        // 5. 检索相关情景记忆（最多3条）
        String memoryText = retrieveRelevantMemories(sessionId, windowMessages);

        return ConversationContext.builder()
                .summary(summary)
                .recentHistory(recentHistoryText)
                .relevantMemories(memoryText)
                .build();
    }

    private String formatMessages(List<ChatHistoryProvider.HistoryMessage> messages) {
        if (messages.isEmpty()) return "";
        return messages.stream()
                .map(m -> (m.role().equals("user") ? "用户: " : "助手: ") + m.content())
                .collect(Collectors.joining("\n"));
    }

    private String retrieveRelevantMemories(String sessionId, List<ChatHistoryProvider.HistoryMessage> recentMessages) {
        if (recentMessages.isEmpty()) return "";
        // 用最后一条用户消息作为查询文本
        String queryText = recentMessages.stream()
                .filter(m -> "user".equals(m.role()))
                .reduce((first, second) -> second) // 取最后一条
                .map(ChatHistoryProvider.HistoryMessage::content)
                .orElse("");
        if (queryText.isBlank()) return "";

        try {
            MemoryQuery query = MemoryQuery.builder()
                    .text(queryText)
                    .sessionId(sessionId)
                    .includeEpisodic(true)
                    .includeSemantic(false)
                    .maxResults(3)
                    .build();
            List<MemoryEntry> entries = memoryAPI.recall(query);
            if (entries.isEmpty()) return "";
            return entries.stream()
                    .map(e -> "- " + (e.getSummary() != null ? e.getSummary() : truncate(e.getContent(), 100)))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("Failed to retrieve memories for session {}", sessionId, e);
            return "";
        }
    }

    private String generateSummary(List<ChatHistoryProvider.HistoryMessage> oldMessages) {
        if (oldMessages.isEmpty()) return "";
        try {
            String historyText = oldMessages.stream()
                    .map(m -> (m.role().equals("user") ? "用户: " : "助手: ") + m.content())
                    .collect(Collectors.joining("\n"));
            ChatClient client = chatClientBuilder.build();
            String prompt = "请用2-3句话总结以下对话的核心内容和关键结论（不超过200字）：\n" + historyText;
            String result = client.prompt(prompt).call().content();
            return result != null ? result.trim() : "";
        } catch (Exception e) {
            log.warn("Summary generation failed, fallback to truncation", e);
            return oldMessages.stream()
                    .map(m -> m.content())
                    .collect(Collectors.joining("; "))
                    .substring(0, Math.min(300, oldMessages.stream().mapToInt(m -> m.content().length()).sum()));
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}