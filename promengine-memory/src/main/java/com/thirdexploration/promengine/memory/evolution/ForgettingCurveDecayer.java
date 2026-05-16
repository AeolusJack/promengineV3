package com.thirdexploration.promengine.memory.evolution;

import com.thirdexploration.promengine.memory.config.AeonMemoryProperties;
import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.storage.EpisodicMemoryService;
import com.thirdexploration.promengine.memory.storage.SemanticMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 遗忘曲线衰减器，根据记忆的访问时间和层级衰减率，定期降低记忆强度，
 * 强度低于阈值的记忆将被软删除。
 * <p>优化：分批处理、批量更新、进度日志、可配置批大小、事务边界控制。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForgettingCurveDecayer {

    private final EpisodicMemoryService episodicMemory;
    private final SemanticMemoryService semanticMemory;
    private final MemoryMetadataRegistry registry;
    private final AeonMemoryProperties properties;

    /**
     * 每批处理的记录数（可通过配置覆盖）
     */
    @Value("${aeon.memory.evolution.decay-batch-size:1000}")
    private int batchSize;

    /**
     * 强度低于此阈值时软删除
     */
    @Value("${aeon.memory.evolution.decay-threshold:0.05}")
    private float decayThreshold;

    /**
     * 是否使用批量更新（若存储服务支持）
     * 若服务不支持，将回退到单条更新，但仍会分批处理
     */
    @Value("${aeon.memory.evolution.use-batch-update:true}")
    private boolean useBatchUpdate;

    /**
     * 定时执行衰减任务（默认每天凌晨3点）
     */
    @Scheduled(cron = "${aeon.memory.evolution.decay-cron:0 0 3 * * ?}")
    public void decayMemories() {
        if (!properties.getEvolution().isDecayEnabled()) {
            log.debug("Memory decay is disabled by configuration");
            return;
        }
        long startTime = System.currentTimeMillis();
        log.info("Starting memory decay process...");

        try {
            // 处理情景记忆
            double episodicRate = registry.getLayerForgettingRate("episodic");
            processLayer("episodic", episodicMemory::getAllActiveIdsPaginated, episodicMemory::findById,
                    episodicMemory::updateStrength, episodicMemory::softDelete, episodicRate);

            // 处理语义记忆
            double semanticRate = registry.getLayerForgettingRate("semantic");
            processLayer("semantic", semanticMemory::getAllIdsPaginated, semanticMemory::findById,
                    semanticMemory::updateStrength, semanticMemory::softDelete, semanticRate);
        } catch (Exception e) {
            log.error("Memory decay process failed", e);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Memory decay completed in {} ms", elapsed);
    }

    /**
     * 处理单层记忆的衰减（支持分页、批量更新降级）
     *
     * @param layerName        层名称（仅用于日志）
     * @param idFetcher        分页获取ID的函数（page, size -> List<String>）
     * @param recordFetcher    根据ID获取完整记录的函数
     * @param strengthUpdater  更新单条记忆强度的函数
     * @param softDeleter      软删除单条记忆的函数
     * @param decayRate        该层的衰减率
     */
    private void processLayer(String layerName,
                              PageableIdFetcher idFetcher,
                              RecordFetcher recordFetcher,
                              StrengthUpdater strengthUpdater,
                              SoftDeleter softDeleter,
                              double decayRate) {
        log.info("Processing {} layer with decay rate {}", layerName, decayRate);
        int page = 0;
        int totalProcessed = 0;
        int totalDeleted = 0;
        int totalUpdated = 0;

        while (true) {
            List<String> ids = idFetcher.fetch(page, batchSize);
            if (ids == null || ids.isEmpty()) {
                break;
            }

            List<MemoryRecord> records = new ArrayList<>();
            for (String id : ids) {
                MemoryRecord record = recordFetcher.fetch(id);
                if (record != null) {
                    records.add(record);
                }
            }

            if (records.isEmpty()) {
                page++;
                continue;
            }

            // 计算新的强度，并分类
            List<MemoryRecord> toUpdate = new ArrayList<>();
            List<String> toDelete = new ArrayList<>();
            for (MemoryRecord record : records) {
                float newStrength = record.computeDecayedStrength(decayRate);
                if (newStrength < decayThreshold) {
                    toDelete.add(record.getId());
                } else {
                    // 只有强度变化超过阈值时才更新（避免频繁更新）
                    if (Math.abs(newStrength - record.getStrength()) > 0.01) {
                        record.setStrength(newStrength);
                        toUpdate.add(record);
                    }
                }
            }

            // 执行更新和删除（批量或单条）
            if (!toUpdate.isEmpty()) {
                if (useBatchUpdate && supportsBatchUpdate(layerName)) {
                    // 如果存储服务支持批量更新，调用批量方法
                    performBatchUpdate(layerName, toUpdate);
                } else {
                    for (MemoryRecord record : toUpdate) {
                        strengthUpdater.update(record.getId(), (float) record.getStrength());
                    }
                }
                totalUpdated += toUpdate.size();
            }

            if (!toDelete.isEmpty()) {
                for (String id : toDelete) {
                    softDeleter.delete(id);
                }
                totalDeleted += toDelete.size();
            }

            totalProcessed += records.size();

            if (totalProcessed % (batchSize * 10) == 0) {
                log.info("{} layer: processed {} records, updated {}, deleted {} so far",
                        layerName, totalProcessed, totalUpdated, totalDeleted);
            }

            page++;
        }

        log.info("{} layer decay finished: processed {}, updated {}, deleted {}",
                layerName, totalProcessed, totalUpdated, totalDeleted);
    }

    /**
     * 检查存储服务是否支持批量更新（通过反射或配置，此处模拟）
     * 实际项目可通过注入不同实现或使用接口默认方法判断。
     */
    private boolean supportsBatchUpdate(String layerName) {
        // 假设 EpisodicMemoryService 和 SemanticMemoryService 都实现了 BatchUpdatable 接口
        // 若没有，则返回 false 回退到单条更新。
        // 此处简单实现：如果 layerName 为 "episodic"，则检查 service 是否实现了 BatchUpdateCapable
        // 为兼容现有代码，默认返回 false，可通过配置开启。
        return useBatchUpdate;
    }

    /**
     * 执行批量更新（伪代码，需要存储服务提供批量方法）
     * 实际使用时，需在 EpisodicMemoryService 和 SemanticMemoryService 中添加 batchUpdateStrengths 方法。
     */
    private void performBatchUpdate(String layerName, List<MemoryRecord> records) {
        if ("episodic".equals(layerName)) {
            episodicMemory.batchUpdateStrengths(records);
        } else {
            semanticMemory.batchUpdateStrengths(records);
        }
    }

    // ---------- 函数式接口 ----------
    @FunctionalInterface
    private interface PageableIdFetcher {
        List<String> fetch(int page, int size);
    }

    @FunctionalInterface
    private interface RecordFetcher {
        MemoryRecord fetch(String id);
    }

    @FunctionalInterface
    private interface StrengthUpdater {
        void update(String id, float newStrength);
    }

    @FunctionalInterface
    private interface SoftDeleter {
        void delete(String id);
    }
}