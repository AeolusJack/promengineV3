package com.thirdexploration.promengine.runtime;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * PromEngine 核心运行时引导配置。
 * 负责触发整个 com.thirdexploration.promengine 包下的组件扫描，
 * 将各模块的 @Component、@Service、@Configuration 等 Bean 统一装配到 Spring 容器中。
 *
 * 此类本身不定义 Bean，仅作为包扫描的入口点。
 */
@Configuration
@ComponentScan(basePackages = "com.thirdexploration.promengine")
public class PromEngineBootstrap {
    // 无需任何代码，注解已完成所有工作
}