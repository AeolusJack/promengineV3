package com.thirdexploration.promengine.core.agent;

import com.thirdexploration.promengine.core.domain.UserInput;
import java.util.List;

/**
 * 对话历史提供者，用于加载指定会话的历史消息。
 * 由 runtime 层实现（避免 executor 直接依赖 runtime）。
 */
public interface ChatHistoryProvider {
    /**
     * 获取会话的最近若干条消息（按时间升序），不包含当前输入。
     * @param sessionId 会话ID
     * @param maxMessages 最大消息条数（用户+助手合计）
     * @return 历史消息列表，格式为统一的接口消息对象
     */
    List<HistoryMessage> getRecentHistory(String sessionId, int maxMessages);

    record HistoryMessage(String role, String content) {
        public static HistoryMessage of(String role, String content) {
            return new HistoryMessage(role, content);
        }
    }
}