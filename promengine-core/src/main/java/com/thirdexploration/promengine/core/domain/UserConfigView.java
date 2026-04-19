package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 用户配置视图。
 */
@Data
@Builder
public class UserConfigView {
    private String userId;
    private String version;
    private Map<String, Object> settings;
}