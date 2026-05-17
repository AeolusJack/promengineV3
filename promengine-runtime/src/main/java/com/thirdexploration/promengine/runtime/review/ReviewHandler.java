package com.thirdexploration.promengine.runtime.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.agent.ReviewRequest;
import com.thirdexploration.promengine.core.agent.ReviewService;
import com.thirdexploration.promengine.neuro.web.RippleWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewHandler implements ReviewService {

    private final RippleWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    /** 存储待审核请求的 Future，key 为 reviewId */
    private final Map<String, CompletableFuture<String>> pendingReviews = new ConcurrentHashMap<>();

    /**
     * 发起审核请求，推送事件到前端并等待决策。
     * @param sessionId 会话 ID（用于 WebSocket 推送目标）
     * @param request   审核请求
     * @return 包含用户决策的 Future（"approve" / "reject"）
     */
    public CompletableFuture<String> requestReview(String sessionId, ReviewRequest request) {
        String reviewId = UUID.randomUUID().toString();
        CompletableFuture<String> future = new CompletableFuture<>();

        pendingReviews.put(reviewId, future);

        // 推送 WebSocket 事件，前端根据 type 处理
        try {
            Map<String, Object> event = Map.of(
                "type", "review_request",
                "reviewId", reviewId,
                "request", request
            );
            webSocketHandler.sendToSession(sessionId, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("推送审核事件失败", e);
            future.completeExceptionally(e);
            pendingReviews.remove(reviewId);
            return future;
        }

        // 设置超时自动拒绝（使用 request.timeoutSeconds）
        return future.orTimeout(request.timeoutSeconds(), TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    pendingReviews.remove(reviewId);
                    return "timeout";
                });
    }

    /**
     * 前端提交决策后调用此方法。
     * @param reviewId 审核 ID
     * @param decision 用户决策（"approve" / "reject"）
     */
    public void submitDecision(String reviewId, String decision) {
        CompletableFuture<String> future = pendingReviews.remove(reviewId);
        if (future != null) {
            future.complete(decision);
        }
    }
}