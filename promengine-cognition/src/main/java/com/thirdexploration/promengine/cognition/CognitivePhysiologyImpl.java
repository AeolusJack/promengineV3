package com.thirdexploration.promengine.cognition;

import com.thirdexploration.promengine.core.CognitivePhysiology;
import com.thirdexploration.promengine.core.domain.SensoryInput;
import com.thirdexploration.promengine.core.domain.TaskContext;
import com.thirdexploration.promengine.core.domain.VitalSigns;
import com.thirdexploration.promengine.cognition.config.CognitionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 认知生理层实现，根据配置动态选择硅基或碳基模式。
 */
@Slf4j
@Component
@Primary   // 关键添加
@RequiredArgsConstructor
public class CognitivePhysiologyImpl implements CognitivePhysiology {

    private final CognitionProperties properties;
    private final SiliconMode siliconMode;
    private final CarbonMode carbonMode;

    private CognitivePhysiology getDelegate() {
        return "carbon".equalsIgnoreCase(properties.getMode()) ? carbonMode : siliconMode;
    }

    @Override
    public VitalSigns tick(SensoryInput input) {
        return getDelegate().tick(input);
    }

    @Override
    public boolean shouldInterrupt(TaskContext ctx) {
        return getDelegate().shouldInterrupt(ctx);
    }

    @Override
    public float getMemoryFidelity() {
        return getDelegate().getMemoryFidelity();
    }

    @Override
    public float getVerbosityFactor() {
        return getDelegate().getVerbosityFactor();
    }

    @Override
    public boolean isInFocusMode() {
        return getDelegate().isInFocusMode();
    }

    @Override
    public int getCurrentFuel() {
        return getDelegate().getCurrentFuel();
    }

    @Override
    public void boostFuel(int amount) {
        getDelegate().boostFuel(amount);
    }
}