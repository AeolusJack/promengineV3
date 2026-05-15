package com.thirdexploration.promengine.core;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 轻量级 Agent 配置对象，用于在编排器中传递 Agent 专属配置，
 * 避免直接依赖 runtime 模块。
 */
@Data
@Builder
public class AgentConfig {
    private String agentId;
    private String systemPrompt;
    private List<String> tools;
    private String modelPreference;
    private String memoryDomain;
    private boolean enableHumanReview;
    private int maxRetries;
    private int timeoutSeconds;
    private String type;
    // 新增：指定规划策略 Skill 名称（可选）
    private String planningSkillName;

}