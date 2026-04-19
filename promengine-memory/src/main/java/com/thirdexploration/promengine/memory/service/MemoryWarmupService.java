package com.thirdexploration.promengine.memory.service;

import com.thirdexploration.promengine.core.MemoryService;
import com.thirdexploration.promengine.memory.config.MemoryProperties;
import com.thirdexploration.promengine.memory.storage.VectorStorage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 记忆索引冷启动预热服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryWarmupService {

    private final MemoryProperties properties;
    private final VectorStorage vectorStorage;
    private final AtomicReference<WarmupStatusImpl> status = new AtomicReference<>(
            new WarmupStatusImpl(false, 0.0, "NOT_STARTED"));

    @PostConstruct
    public void init() {
        if (properties.getWarmup().isAsync()) {
            CompletableFuture.runAsync(this::warmup);
        } else {
            warmup();
        }
    }

    private void warmup() {
        status.set(new WarmupStatusImpl(false, 0.0, "LOADING_VECTORS"));
        log.info("Starting memory warmup...");
        long start = System.currentTimeMillis();
        try {
            // 模拟向量索引加载（LanceDB 内部已处理，此处可重建索引）
            vectorStorage.rebuildIndex();
            status.set(new WarmupStatusImpl(true, 1.0, "COMPLETED"));
            log.info("Memory warmup completed in {} ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Memory warmup failed", e);
            status.set(new WarmupStatusImpl(false, 0.0, "FAILED: " + e.getMessage()));
        }
    }

    public MemoryService.WarmupStatus getStatus() {
        return status.get();
    }

    private record WarmupStatusImpl(boolean complete, double progress, String currentPhase)
            implements MemoryService.WarmupStatus {
        @Override
        public boolean isComplete() { return complete; }
        @Override
        public double getProgress() { return progress; }
        @Override
        public String getCurrentPhase() { return currentPhase; }
    }
}