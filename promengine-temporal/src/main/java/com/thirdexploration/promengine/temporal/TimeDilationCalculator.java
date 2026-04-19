package com.thirdexploration.promengine.temporal;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TimeDilationCalculator {

    private final AtomicReference<Double> currentFactor = new AtomicReference<>(1.0);
    private volatile Instant lastUpdate = Instant.now();

    public void update(double eventDensity) {
        // 事件密度越高，主观时间越慢（因子越小）
        double newFactor = Math.max(0.5, Math.min(2.0, 1.0 / (1.0 + eventDensity)));
        currentFactor.set(newFactor);
        lastUpdate = Instant.now();
    }

    public double getCurrentFactor() {
        // 随时间衰减回归 1.0
        Instant now = Instant.now();
        double factor = currentFactor.get();
        long secondsSinceUpdate = Duration.between(lastUpdate, now).getSeconds();
        if (secondsSinceUpdate > 60) {
            double decay = Math.min(1.0, secondsSinceUpdate / 3600.0);
            factor = factor + (1.0 - factor) * decay;
            currentFactor.set(factor);
        }
        return factor;
    }
}