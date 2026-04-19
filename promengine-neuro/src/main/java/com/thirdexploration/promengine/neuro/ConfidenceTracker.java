package com.thirdexploration.promengine.neuro;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConfidenceTracker {

    private final Map<String, Double> tokenConfidences = new ConcurrentHashMap<>();

    public void record(String token, double confidence) {
        tokenConfidences.put(token, confidence);
    }

    public double getAverageConfidence() {
        return tokenConfidences.values().stream()
                .mapToDouble(Double::doubleValue)
                .average().orElse(1.0);
    }

    public void clear() {
        tokenConfidences.clear();
    }
}