package com.thirdexploration.promengine.core.agent;

import java.util.Map;

/**
 * 上下文提供者接口，各垂直领域 Agent 可实现此接口，
 * 为 PromptPipeline 注入领域专属上下文。
 */
public interface ContextProvider {
    /** 返回提供者类型，如 "code", "finance" */
    String getType();

    /** 收集上下文数据 */
    Map<String, Object> collect(String userId, String sessionId, String projectId);
}