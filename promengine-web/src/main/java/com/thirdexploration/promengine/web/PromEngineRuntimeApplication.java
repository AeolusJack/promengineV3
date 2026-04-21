package com.thirdexploration.promengine.web;

import com.thirdexploration.promengine.runtime.PromEngineBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import(PromEngineBootstrap.class)   // 显式导入运行时核心配置
public class PromEngineRuntimeApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromEngineRuntimeApplication.class, args);
    }
}