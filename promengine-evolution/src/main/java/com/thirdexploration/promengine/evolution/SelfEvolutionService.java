package com.thirdexploration.promengine.evolution;

import com.thirdexploration.promengine.evolution.config.EvolutionProperties;
import com.thirdexploration.promengine.memory.service.DefaultMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 自我进化服务：夜间运行反思、对抗训练等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfEvolutionService {

    private final DefaultMemoryService memoryService;
    private final RegretEngine regretEngine;
    private final EvolutionProperties properties;

    @Scheduled(cron = "0 0 3 * * ?")
    public void nightlyReflection() {
        if (!properties.isReflectionEnabled()) return;
        log.info("Starting nightly reflection...");
        memoryService.reflect();
        log.info("Nightly reflection completed");
    }

    @Scheduled(cron = "0 0 4 * * ?")
    public void adversarialTraining() {
        if (!properties.isAdversarialEnabled()) return;
        log.info("Starting adversarial self-play...");
        // 实现对抗性博弈逻辑
        log.info("Adversarial training completed");
    }
}