package com.thirdexploration.promengine.memory.governance;

import com.thirdexploration.promengine.memory.config.AeonMemoryProperties;
import com.thirdexploration.promengine.memory.model.MemoryMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * aeon
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryGovernor {

    private final AeonMemoryProperties properties;

    public boolean shouldStore(String content, MemoryMetadata metadata) {
        if (!properties.getGovernance().isEnabled()) {
            return true;
        }
        // 基础过滤：空内容、敏感词等
        if (content == null || content.isBlank()) {
            return false;
        }
        return true;
    }
}