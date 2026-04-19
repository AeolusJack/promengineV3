package com.thirdexploration.promengine.cognition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 防御机制引擎（碳基专属）。
 */
@Slf4j
@Component
public class DefenseMechanism {

    public enum DefenseType {
        HESITATION,      // 犹豫
        CLARIFICATION,   // 请求澄清
        RATIONALIZATION, // 合理化
        SUBLIMATION      // 升华
    }

    public DefenseResponse applyDefense(String userIntent, float conflictScore) {
        if (conflictScore < 0.3) return DefenseResponse.none();

        DefenseType type;
        String responsePrefix;
        if (conflictScore < 0.6) {
            type = DefenseType.HESITATION;
            responsePrefix = "我需要一点时间考虑一下... ";
        } else {
            type = DefenseType.CLARIFICATION;
            responsePrefix = "你是希望我帮你做决定，还是只是想听听我的想法？ ";
        }

        log.debug("Defense triggered: type={}, conflictScore={}", type, conflictScore);
        return new DefenseResponse(type, responsePrefix, true);
    }

    public record DefenseResponse(DefenseType type, String prefix, boolean shouldDelay) {
        public static DefenseResponse none() {
            return new DefenseResponse(null, "", false);
        }
    }
}