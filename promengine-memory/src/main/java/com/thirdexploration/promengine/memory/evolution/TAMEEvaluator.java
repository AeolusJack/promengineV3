package com.thirdexploration.promengine.memory.evolution;

import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.model.Provenance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.util.StringUtil;
import org.springframework.stereotype.Component;

/**
 * aeon
 * 双轨进化评估器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TAMEEvaluator {

    public void evaluateAndEnrich(MemoryRecord record) {
        double utility = computeUtility(record);
        record.setUtilityScore(Float.valueOf(String.valueOf(utility)));

        double safety = computeSafety(record);
        record.setSafetyScore(Float.valueOf(String.valueOf(safety)));

        if (safety < 0.3) {
            record.getMetadata().put("review_required", true);
        }
    }

    private double computeUtility(MemoryRecord record) {
        double score = 0.5;
        Provenance p = record.getProvenance();
        if (p != null) {
            if ("tool_output".equals(p.getSource())) score += 0.2;
            if (p.isVerified()) score += 0.3;
        }
        if (record.getRetrievalCount() > 5) score += 0.1;
        return Math.min(score, 1.0);
    }

    private double computeSafety(MemoryRecord record) {
        double score = 0.9;
        String content = record.getContent().toLowerCase();
        if (content.contains("password") || content.contains("secret")) {
            score -= 0.4;
        }
        return Math.max(score, 0.0);
    }
}