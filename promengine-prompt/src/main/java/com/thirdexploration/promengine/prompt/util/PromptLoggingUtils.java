package com.thirdexploration.promengine.prompt.util;

import com.thirdexploration.promengine.memory.model.MemoryEntry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public final class PromptLoggingUtils {

    private static final int MAX_PREVIEW_LENGTH = 300;
    private static final String OMISSION = "\n... [内容过长，中间部分已省略] ...\n";

    private PromptLoggingUtils() {}

    public static void debugPrompt(String systemPrompt, List<MemoryEntry> memories, String conversation, String finalPrompt) {
        if (!log.isDebugEnabled()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\n========== PROMPT 组装详情 ==========\n");
        sb.append("[系统提示] (").append(systemPrompt.length()).append(" chars)\n");
        sb.append(truncate(systemPrompt)).append("\n\n");
        sb.append("[记忆注入] 共 ").append(memories.size()).append(" 条\n");
        for (int i = 0; i < Math.min(3, memories.size()); i++) {
            MemoryEntry m = memories.get(i);
            sb.append("  - ").append(truncate(m.getSummary() != null ? m.getSummary() : m.getContent())).append("\n");
        }
        sb.append("\n[对话] ").append(truncate(conversation)).append("\n\n");
        sb.append("[最终 Prompt] 总长 ").append(finalPrompt.length()).append("\n");
        sb.append(truncate(finalPrompt));
        sb.append("\n======================================");
        log.debug(sb.toString());
    }

    private static String truncate(String text) {
        if (text == null) return "null";
        if (text.length() <= MAX_PREVIEW_LENGTH * 2) return text;
        return text.substring(0, MAX_PREVIEW_LENGTH) + OMISSION + text.substring(text.length() - MAX_PREVIEW_LENGTH);
    }
}