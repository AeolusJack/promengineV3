package com.thirdexploration.promengine.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.thirdexploration.promengine")
@EnableScheduling
public class PromEngineRuntimeApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromEngineRuntimeApplication.class, args);
    }
}