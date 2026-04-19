package com.thirdexploration.promengine.cognition;

import com.thirdexploration.promengine.core.CognitivePhysiology;
import com.thirdexploration.promengine.core.domain.SensoryInput;
import com.thirdexploration.promengine.core.domain.VitalSigns;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 生命体征监控，定期 tick 更新状态。
 */
@Component
@RequiredArgsConstructor
public class VitalSignsMonitor {

    private final CognitivePhysiology physiology;

    @Scheduled(fixedDelay = 100) // 100ms tick（碳基模式有效）
    public void tick() {
        SensoryInput empty = SensoryInput.builder().build();
        VitalSigns signs = physiology.tick(empty);
        // 可推送至 WebSocket 或存储
    }
}