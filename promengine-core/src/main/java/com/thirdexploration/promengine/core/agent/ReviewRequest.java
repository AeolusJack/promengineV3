package com.thirdexploration.promengine.core.agent;

import java.util.List;

/**
 * 人机协作审核请求。
 */
public record ReviewRequest(
    String type,          // 如 "code_diff", "investment_decision"
    String title,
    String content,       // JSON 或文本
    List<String> options, // 可选决策
    int timeoutSeconds
) {}