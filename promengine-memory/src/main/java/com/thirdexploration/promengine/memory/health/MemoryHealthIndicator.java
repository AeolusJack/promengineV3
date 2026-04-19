package com.thirdexploration.promengine.memory.health;

import com.thirdexploration.promengine.memory.config.MemoryProperties;
import com.thirdexploration.promengine.memory.storage.HotStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@RequiredArgsConstructor
public class MemoryHealthIndicator implements HealthIndicator {

    private final HotStorage hotStorage;
    private final MemoryProperties properties;

    @Value("${promengine.data-dir:./data}")  // ✅ 直接从全局配置注入
    private String dataDir;

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        try {
            hotStorage.findById("health-check");
            builder.up();
        } catch (Exception e) {
            builder.down(e);
        }

        File memoryDataDir = new File(dataDir, "memory");
        if (memoryDataDir.exists()) {
            long total = memoryDataDir.getTotalSpace();
            long free = memoryDataDir.getFreeSpace();
            double usedRatio = 1.0 - (double) free / total;
            builder.withDetail("diskUsageRatio", usedRatio);
            if (usedRatio > 0.9) {
                builder.status("WARN");
            }
        }
        return builder.build();
    }
}