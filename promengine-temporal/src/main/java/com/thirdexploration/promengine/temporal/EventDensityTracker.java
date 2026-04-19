package com.thirdexploration.promengine.temporal;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class EventDensityTracker {

    private final AtomicInteger recentEventCount = new AtomicInteger(0);
    private volatile Instant windowStart = Instant.now();
    private static final int WINDOW_SECONDS = 300; // 5分钟

    public void recordEvent() {
        recentEventCount.incrementAndGet();
        checkWindow();
    }

    public double getDensity() {
        checkWindow();
        return recentEventCount.get() / (double) WINDOW_SECONDS;
    }

    private void checkWindow() {
        Instant now = Instant.now();
        if (Duration.between(windowStart, now).getSeconds() > WINDOW_SECONDS) {
            recentEventCount.set(0);
            windowStart = now;
        }
    }
}