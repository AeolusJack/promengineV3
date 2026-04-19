package com.thirdexploration.promengine.ethics;

import com.thirdexploration.promengine.core.domain.TaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EthicalGovernor {

    private final EthicalDecisionMatrix decisionMatrix;
    private final AuditLogger auditLogger;
    private final EthicsProperties properties;

    public EthicalDecision evaluate(TaskContext ctx) {
        if (!properties.isEnabled()) {
            return EthicalDecision.ALLOW;
        }

        EthicalDecision decision = decisionMatrix.evaluate(ctx);
        auditLogger.log(ctx, decision);
        return decision;
    }

    public enum EthicalDecision {
        ALLOW, BLOCK, REQUIRE_CONFIRMATION
    }
}