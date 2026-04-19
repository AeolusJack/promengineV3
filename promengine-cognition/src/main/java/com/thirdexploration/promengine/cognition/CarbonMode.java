package com.thirdexploration.promengine.cognition;

import com.thirdexploration.promengine.core.CognitivePhysiology;
import com.thirdexploration.promengine.core.domain.SensoryInput;
import com.thirdexploration.promengine.core.domain.TaskContext;
import com.thirdexploration.promengine.core.domain.VitalSigns;
import com.thirdexploration.promengine.cognition.config.CognitionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class CarbonMode implements CognitivePhysiology {

    private final CognitiveFuelManager fuelManager;
    private final CognitionProperties properties;
    private final AtomicInteger focusCounter = new AtomicInteger(0);
    private volatile Instant lastActivity = Instant.now();

    @Override
    public VitalSigns tick(SensoryInput input) {
        lastActivity = Instant.now();
        int fuel = fuelManager.getCurrentFuel();
        // 根据输入情感调整情绪基调
        String tone = input.getSentimentScore() < -0.3 ? "low" : "neutral";
        return VitalSigns.builder()
                .cognitiveFuel(fuel)
                .focusLevel(fuel > 50 ? 1.0f : fuel / 100.0f)
                .resting(false)
                .emotionalTone(tone)
                .build();
    }

    @Override
    public boolean shouldInterrupt(TaskContext ctx) {
        // 燃料极低时建议打断
        return fuelManager.getCurrentFuel() < 10;
    }

    @Override
    public float getMemoryFidelity() {
        // 燃料越低记忆保真度越差
        return 0.7f + 0.3f * (fuelManager.getCurrentFuel() / 100.0f);
    }

    @Override
    public float getVerbosityFactor() {
        int fuel = fuelManager.getCurrentFuel();
        if (fuel < 20) return 0.3f;
        if (fuel < 50) return 0.7f;
        return 1.0f;
    }

    @Override
    public boolean isInFocusMode() {
        return focusCounter.get() > 0;
    }

    @Override
    public int getCurrentFuel() {
        return fuelManager.getCurrentFuel();
    }

    @Override
    public void boostFuel(int amount) {
        fuelManager.boost(amount);
    }

    public void enterFocusMode() {
        focusCounter.incrementAndGet();
    }

    public void exitFocusMode() {
        focusCounter.decrementAndGet();
    }
}