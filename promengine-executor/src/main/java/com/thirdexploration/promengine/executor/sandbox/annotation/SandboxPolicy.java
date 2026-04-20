package com.thirdexploration.promengine.executor.sandbox.annotation;

import java.lang.annotation.*;

/**
 * 声明工具的安全策略，由 SandboxManager 在运行时强制应用。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SandboxPolicy {

    /** 允许访问的工作区子路径，空数组表示无限制 */
    String[] allowedPaths() default {};

    /** 是否允许网络访问 */
    boolean allowNetwork() default false;

    /** 允许访问的域名白名单 */
    String[] allowedDomains() default {};

    /** 最大内存限制（MB） */
    int maxMemoryMB() default 64;

    /** 最大执行时间（秒） */
    int maxExecutionSeconds() default 30;

    /** 是否需要用户二次确认（高危操作） */
    boolean requireConfirmation() default false;
}