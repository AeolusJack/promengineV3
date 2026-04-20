package com.thirdexploration.promengine.executor.tool.annotation;

import java.lang.annotation.*;

/**
 * 标注工具执行方法的参数，用于自动生成 JSON Schema 和参数校验。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolParameter {

    /** 参数名称 */
    String value();

    /** 参数描述 */
    String description() default "";

    /** 是否必须 */
    boolean required() default true;

    /** 示例值（增强 LLM 理解） */
    String example() default "";

    /** 是否包含敏感信息（用于日志脱敏） */
    boolean sensitive() default false;

    /** 允许的值列表（枚举约束） */
    String[] allowedValues() default {};

    /** 正则校验 */
    String pattern() default "";
}