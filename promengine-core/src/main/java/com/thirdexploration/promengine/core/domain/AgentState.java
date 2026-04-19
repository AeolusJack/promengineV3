package com.thirdexploration.promengine.core.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 运行时状态快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentState {
    private String mode;                     // "silicon" 或 "carbon"
    private boolean running;
    private VitalSigns vitalSigns;
    private int activeMicroAgents;
    private long memoryEntriesTotal;
}