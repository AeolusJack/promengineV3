package com.thirdexploration.promengine.cognition;

import com.thirdexploration.promengine.core.CognitivePhysiology;
import com.thirdexploration.promengine.core.domain.SensoryInput;
import com.thirdexploration.promengine.core.domain.TaskContext;
import com.thirdexploration.promengine.core.domain.VitalSigns;
import org.springframework.stereotype.Component;

@Component
public class SiliconMode implements CognitivePhysiology {

    @Override
    public VitalSigns tick(SensoryInput input) {
        return VitalSigns.builder()
                .cognitiveFuel(100)
                .focusLevel(1.0f)
                .resting(false)
                .emotionalTone("neutral")
                .build();
    }

    @Override
    public boolean shouldInterrupt(TaskContext ctx) {
        return false;
    }

    @Override
    public float getMemoryFidelity() {
        return 1.0f;
    }

    @Override
    public float getVerbosityFactor() {
        return 1.0f;
    }

    @Override
    public boolean isInFocusMode() {
        return true;
    }

    @Override
    public int getCurrentFuel() {
        return 100;
    }

    @Override
    public void boostFuel(int amount) {
        // 硅基模式无燃料概念
    }
}