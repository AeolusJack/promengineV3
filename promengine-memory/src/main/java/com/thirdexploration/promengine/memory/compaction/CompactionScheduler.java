package com.thirdexploration.promengine.memory.compaction;

import com.thirdexploration.promengine.memory.config.MemoryProperties;
import com.thirdexploration.promengine.memory.storage.WarmStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 温存储归并定时任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompactionScheduler {

    private final WarmStorage warmStorage;
    private final MemoryProperties properties;

    @Scheduled(cron = "${promengine.memory.warm-storage.compaction.schedule:0 0 3 1 * ?}")
    public void runCompaction() {
        if (!properties.getWarmStorage().getCompaction().isEnabled()) {
            log.debug("Compaction is disabled");
            return;
        }
        log.info("Starting scheduled warm storage compaction");
        List<String> partitions = warmStorage.listPartitions();
        // 仅处理非当前月份的分区（避免频繁重写）
        String currentMonth = java.time.YearMonth.now().toString();
        for (String partition : partitions) {
            if (partition.equals(currentMonth)) continue;
            try {
                int count = warmStorage.compact(partition);
                log.info("Compacted partition {} with {} records", partition, count);
            } catch (Exception e) {
                log.error("Compaction failed for partition {}", partition, e);
            }
        }
    }
}