package com.thirdexploration.promengine.core.agent;

import java.util.concurrent.CompletableFuture;

/**
 * 审核服务接口，用于向用户发送审核请求并异步等待决策。
 * 由 runtime 层实现（ReviewHandler），避免 executor 直接依赖 runtime。
 */
public interface ReviewService {
    /**
     * 请求用户审核
     * @param sessionId  WebSocket 会话 ID
     * @param request    审核内容
     * @return 包含用户决策的 Future（"approve" / "reject" / "timeout"）
     */
    CompletableFuture<String> requestReview(String sessionId, ReviewRequest request);

    /**
     * 提交审核决策（前端调用）
     */
    void submitDecision(String reviewId, String decision);
}