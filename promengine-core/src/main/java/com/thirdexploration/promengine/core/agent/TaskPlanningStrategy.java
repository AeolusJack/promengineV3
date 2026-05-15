package com.thirdexploration.promengine.core.agent;

import java.util.List;
import java.util.Map;

/**
 * 任务规划策略接口，由各垂直领域或用户自定义实现。
 */
public interface TaskPlanningStrategy {
    /**
     * 根据用户意图生成任务计划
     * @param userIntent 用户的原始请求
     * @param context 当前执行上下文（包含已有项目信息等）
     * @return 任务步骤列表
     */
    List<TaskPlan.Step> generatePlan(String userIntent, Map<String, Object> context);
}