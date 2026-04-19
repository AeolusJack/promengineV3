package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 生命体征。
 */
@Data
@Builder
public class VitalSigns {
    private int cognitiveFuel;           // 0-100
    private float focusLevel;            // 0-1
    private boolean resting;
    private String emotionalTone;
}