package com.thirdexploration.promengine.evolution;

import com.thirdexploration.promengine.core.domain.TaskContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 遗憾引擎：基于对比式遗憾挖掘，无需额外模型调用。
 */
@Slf4j
@Component
public class RegretEngine {

    private final Map<String, DecisionFork> pendingDecisions = new ConcurrentHashMap<>();

    /**
     * 记录决策分叉点
     */
    public void recordFork(String sessionId, TaskContext ctx, List<String> options) {
        DecisionFork fork = DecisionFork.builder()
                .sessionId(sessionId)
                .context(ctx)
                .options(options)
                .timestamp(System.currentTimeMillis())
                .build();
        pendingDecisions.put(sessionId, fork);
        log.debug("Recorded decision fork for session {}", sessionId);
    }

    /**
     * 根据用户后续反馈计算遗憾向量
     */
    public RegretVector computeRegret(String sessionId, String chosenOption, boolean userSatisfied) {
        DecisionFork fork = pendingDecisions.remove(sessionId);
        if (fork == null) return RegretVector.empty();

        if (userSatisfied) {
            return RegretVector.empty(); // 满意则不产生遗憾
        }

        // 简单遗憾计算：未被选中的选项中若有预期收益更高的，产生遗憾
        double maxExpected = fork.getOptions().stream()
                .filter(opt -> !opt.equals(chosenOption))
                .mapToDouble(this::estimateExpectedUtility)
                .max().orElse(0.0);
        double chosenUtility = estimateExpectedUtility(chosenOption);
        double regret = Math.max(0, maxExpected - chosenUtility);

        RegretVector vector = RegretVector.builder()
                .sessionId(sessionId)
                .context(fork.getContext())
                .chosenOption(chosenOption)
                .regretScore(regret)
                .build();

        log.info("Computed regret score {} for session {}", regret, sessionId);
        return vector;
    }

    private double estimateExpectedUtility(String option) {
        // 启发式评估，实际可基于历史成功率
        return 0.5;
    }

    /**
     * 应用遗憾向量到人格权重微调（实验性）
     */
    public void applyRegretToPersonality(RegretVector vector) {
        if (vector.getRegretScore() < 0.1) return;
        // 通知人格服务调整参数，此处仅打日志
        log.info("Applying regret vector to personality: {}", vector);
    }
}