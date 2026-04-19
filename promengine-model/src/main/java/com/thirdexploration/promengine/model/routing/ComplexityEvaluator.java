package com.thirdexploration.promengine.model.routing;

import org.springframework.stereotype.Component;

@Component
public class ComplexityEvaluator {

    public double evaluate(String prompt) {
        // 简单启发式：长度 + 关键词
        int length = prompt.length();
        double score = Math.min(length / 500.0, 1.0);
        if (prompt.contains("代码") || prompt.contains("code")) {
            score += 0.3;
        }
        return Math.min(score, 1.0);
    }
}