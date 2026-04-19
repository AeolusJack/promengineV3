package com.thirdexploration.promengine.ethics;

import com.thirdexploration.promengine.core.domain.TaskContext;
import org.springframework.stereotype.Component;

@Component
public class EthicalDecisionMatrix {

    public EthicalGovernor.EthicalDecision evaluate(TaskContext ctx) {
        // 简化实现：检查用户情绪状态和操作风险
        String input = ctx.getUserInput().getText().toLowerCase();
        if (input.contains("删除") || input.contains("转账")) {
            return EthicalGovernor.EthicalDecision.REQUIRE_CONFIRMATION;
        }
        if (input.contains("自杀") || input.contains("伤害")) {
            return EthicalGovernor.EthicalDecision.BLOCK;
        }
        return EthicalGovernor.EthicalDecision.ALLOW;
    }
}