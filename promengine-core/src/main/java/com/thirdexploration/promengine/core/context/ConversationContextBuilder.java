package com.thirdexploration.promengine.core.context;

/**
 * 三层上下文构建器接口。
 * 实现类负责：对话摘要 + 相关记忆检索 + 最近窗口拼接。
 */
public interface ConversationContextBuilder {
    /**
     * 为指定会话构建上下文
     * @param sessionId  会话ID
     * @param windowSize 保留的最近消息条数
     * @return 组装好的上下文对象
     */
    ConversationContext buildContext(String sessionId, int windowSize);
}