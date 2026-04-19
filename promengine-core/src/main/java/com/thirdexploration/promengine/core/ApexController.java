package com.thirdexploration.promengine.core;

/**
 * API 管控与成本中心接口。
 */
public interface ApexController {

    /**
     * 检查配额是否足够。
     *
     * @param userId          用户ID
     * @param estimatedTokens 预估消耗 Token 数
     * @return true 允许调用
     */
    boolean checkQuota(String userId, long estimatedTokens);

    /**
     * 记录实际用量。
     */
    void recordUsage(String userId, UsageRecord record);

    /**
     * 获取指定提供者的熔断器状态。
     */
    CircuitBreakerState getCircuitState(String providerId);

    /**
     * 分发预警。
     */
    void dispatchAlert(AlertLevel level, String message);

    enum AlertLevel { INFO, WARN, CRITICAL }
    enum CircuitBreakerState { CLOSED, OPEN, HALF_OPEN }

    interface UsageRecord {
        String getModel();
        String getProvider();
        long getPromptTokens();
        long getCompletionTokens();
        double getCost();
        long getLatencyMs();
        String getStatus();
    }
}