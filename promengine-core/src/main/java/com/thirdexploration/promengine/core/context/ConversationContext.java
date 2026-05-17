package com.thirdexploration.promengine.core.context;

import lombok.Builder;
import lombok.Data;

/**
 * 三层上下文数据载体。
 */
@Data
@Builder
public class ConversationContext {
    /** 早期对话摘要 */
    private String summary;
    /** 最近窗口内的原文对话文本（已格式化为 user/assistant 角色标记） */
    private String recentHistory;
    /** 从情景记忆中检索到的相关记忆文本 */
    private String relevantMemories;

    /**
     * 将所有上下文组合为一段适合注入 Prompt 的文本。
     */
    public String toPromptSection() {
        StringBuilder sb = new StringBuilder();
        if (summary != null && !summary.isBlank()) {
            sb.append("[早期对话摘要]\n").append(summary).append("\n\n");
        }
        if (relevantMemories != null && !relevantMemories.isBlank()) {
            sb.append("[相关历史记忆]\n").append(relevantMemories).append("\n\n");
        }
        if (recentHistory != null && !recentHistory.isBlank()) {
            sb.append("[最近对话]\n").append(recentHistory).append("\n\n");
        }
        return sb.toString();
    }
}