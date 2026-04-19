package com.thirdexploration.promengine.apex;

import com.thirdexploration.promengine.core.ApexController;
import com.thirdexploration.promengine.core.exception.QuotaExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApexControllerImpl implements ApexController {

    private final QuotaManager quotaManager;
    private final CostTracker costTracker;
    private final CircuitBreakerManager circuitBreakerManager;
    private final AuditLogger auditLogger;
    private final ApexProperties properties;

    @Override
    public boolean checkQuota(String userId, long estimatedTokens) {
        if (!properties.isEnabled()) return true;
        return quotaManager.checkAndReserve(userId, estimatedTokens);
    }

    @Override
    public void recordUsage(String userId, UsageRecord record) {
        if (!properties.isEnabled()) return;
        costTracker.record(userId, record);
        auditLogger.log(userId, record);
        quotaManager.deduct(userId, record.getPromptTokens() + record.getCompletionTokens());
    }

    @Override
    public CircuitBreakerState getCircuitState(String providerId) {
        return circuitBreakerManager.getState(providerId);
    }

    @Override
    public void dispatchAlert(AlertLevel level, String message) {
        log.warn("APEX Alert [{}]: {}", level, message);
        // 可集成飞书/邮件通知
    }
}