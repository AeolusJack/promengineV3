package com.thirdexploration.promengine.core.agent;

import com.thirdexploration.promengine.core.AgentConfig;

/**
 * Agent 配置提供者接口，由 runtime 模块实现。
 * 编排器通过此接口获取 Agent 配置，实现依赖倒置。
 */
public interface AgentConfigProvider {
    AgentConfig getConfig(String agentId);
}