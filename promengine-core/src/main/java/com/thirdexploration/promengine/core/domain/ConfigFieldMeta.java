package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 配置字段元数据（供前端生成表单）。
 */
@Data
@Builder
public class ConfigFieldMeta {
    private String key;
    private String displayName;
    private String description;
    private ConfigType type;
    private boolean userModifiable;
    private boolean requiresApproval;
    private boolean requiresRestart;
    private List<String> dependsOn;
    private Object defaultValue;
    private Object currentValue;
    private Map<String, Object> constraints;

    public enum ConfigType {
        STRING, NUMBER, BOOLEAN, ENUM, DURATION, JSON
    }
}