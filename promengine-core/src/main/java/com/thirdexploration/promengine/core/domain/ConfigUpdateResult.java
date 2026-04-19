package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 配置更新结果。
 */
@Data
@Builder
public class ConfigUpdateResult {
    private boolean success;
    private String changeId;
    private boolean requiresApproval;
    private boolean requiresRestart;
    private List<String> validationErrors;
}