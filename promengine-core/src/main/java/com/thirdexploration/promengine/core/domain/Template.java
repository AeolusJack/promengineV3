package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 提示词模板定义。
 */
@Data
@Builder
public class Template {
    private String id;
    private String name;
    private String version;
    private String content;   // Jinja2 风格模板内容
    private String mode;      // "carbon" / "silicon"
}