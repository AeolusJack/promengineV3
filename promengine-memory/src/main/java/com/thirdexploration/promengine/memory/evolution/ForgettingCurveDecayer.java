package com.thirdexploration.promengine.memory.evolution;

import com.thirdexploration.promengine.memory.config.AeonMemoryProperties;
import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.storage.EpisodicMemoryService;
import com.thirdexploration.promengine.memory.storage.SemanticMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * aeon
 * 遗忘曲线衰减器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForgettingCurveDecayer {

    private final EpisodicMemoryService episodicMemory;
    private final SemanticMemoryService semanticMemory;
    private final MemoryMetadataRegistry registry;
    private final AeonMemoryProperties properties;

    @Scheduled(cron = "${aeon.memory.evolution.decay-cron:0 0 3 * * ?}")
    public void decayMemories() {
        if (!properties.getEvolution().isDecayEnabled()) {
            return;
        }
        log.info("Starting memory decay...");

        // 处理情景记忆
        double episodicRate = registry.getLayerForgettingRate("episodic");
        for (String id : episodicMemory.getAllActiveIds()) {
            var record = episodicMemory.findById(id);
            if (record != null) {
                float newStrength = record.computeDecayedStrength(episodicRate);
                if (newStrength < 0.05f) {
                    episodicMemory.softDelete(id);
                } else {
                    episodicMemory.updateStrength(id, newStrength);
                }
            }
        }

        // 处理语义记忆
        double semanticRate = registry.getLayerForgettingRate("semantic");
        for (String id : semanticMemory.getAllIds()) {
            var record = semanticMemory.findById(id);
            if (record != null) {
                float newStrength = record.computeDecayedStrength(semanticRate);
                if (newStrength < 0.05f) {
                    semanticMemory.softDelete(id);
                } else {
                    semanticMemory.updateStrength(id, newStrength);
                }
            }
        }

        log.info("Memory decay completed");
    }
}