package com.thirdexploration.promengine.neuro;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 元认知监控，跟踪模型置信度变化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetaCognitionMonitor {

    private final ConfidenceTracker confidenceTracker;
    private final ThinkingRippleGenerator rippleGenerator;
    private final Deque<Double> recentConfidences = new ArrayDeque<>();
    private static final int WINDOW_SIZE = 50;

    public void onTokenGenerated(String token, double confidence) {
        recentConfidences.addLast(confidence);
        if (recentConfidences.size() > WINDOW_SIZE) {
            recentConfidences.removeFirst();
        }
        confidenceTracker.record(token, confidence);

        // 计算局部熵值变化，生成涟漪
        double avg = recentConfidences.stream().mapToDouble(Double::doubleValue).average().orElse(0.5);
        rippleGenerator.generate(avg);
    }

    public boolean shouldAddDisclaimers() {
        double avg = recentConfidences.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
        return avg < 0.6;
    }

    public String getDisclaimer() {
        return "我不太确定这个信息，需要我帮你再确认一下吗？";
    }
}