package com.thirdexploration.promengine.core.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.ConfigManagementService;
import com.thirdexploration.promengine.core.domain.*;
import com.thirdexploration.promengine.core.util.IdGenerator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultConfigManagementService implements ConfigManagementService {

    private final ObjectMapper objectMapper;

    // 内存存储当前配置
    private final Map<String, Object> currentConfig = new ConcurrentHashMap<>();
    // 配置变更历史，key 为 changeId
    private final Map<String, ConfigChangeRecord> changeHistory = new ConcurrentHashMap<>();
    // 用户配置快照，key 为版本号（时间戳字符串）
    private final List<ConfigSnapshot> snapshots = new ArrayList<>();

    private static final Path CONFIG_FILE_PATH = Paths.get("./configs/promengine-config.json");
    private static final Path SNAPSHOT_DIR = Paths.get("./configs/snapshots");

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(SNAPSHOT_DIR);
            loadCurrentConfig();
            loadSnapshots();
            log.info("ConfigManagementService initialized with {} current entries", currentConfig.size());
        } catch (Exception e) {
            log.error("Failed to initialize ConfigManagementService", e);
            // 初始化默认配置
            initDefaultConfig();
        }
    }

    private void initDefaultConfig() {
        currentConfig.put("promengine.mode", "carbon");
        currentConfig.put("promengine.cognition.focus-mode", "auto");
        currentConfig.put("promengine.memory.retrieval.default-time-window", "30d");
        currentConfig.put("promengine.proactive.l1-enabled", true);
        // 其他默认配置...
        saveCurrentConfig();
    }

    @Override
    public UserConfigView getUserConfig(String userId) {
        return UserConfigView.builder()
                .userId(userId)
                .version(String.valueOf(System.currentTimeMillis()))
                .settings(new HashMap<>(currentConfig))
                .build();
    }

    @Override
    public ConfigUpdateResult updateConfig(String userId, Map<String, Object> updates) {
        String changeId = IdGenerator.generateWithPrefix("cfg");
        // 记录变更前快照
        String version = String.valueOf(System.currentTimeMillis());
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .version(version)
                .timestamp(Instant.now())
                .config(new HashMap<>(currentConfig))
                .build();
        snapshots.add(snapshot);
        saveSnapshot(snapshot);

        // 应用更新
        currentConfig.putAll(updates);
        saveCurrentConfig();

        ConfigChangeRecord record = ConfigChangeRecord.builder()
                .changeId(changeId)
                .userId(userId)
                .timestamp(Instant.now())
                .updates(updates)
                .previousVersion(version)
                .build();
        changeHistory.put(changeId, record);

        log.info("Config updated by {}, changeId={}, updates={}", userId, changeId, updates);

        return ConfigUpdateResult.builder()
                .success(true)
                .changeId(changeId)
                .requiresApproval(false) // 简化为无需审批
                .requiresRestart(false)
                .validationErrors(Collections.emptyList())
                .build();
    }

    @Override
    public ConfigUpdateResult approvePendingChange(String userId, String changeId) {
        // 简化实现，直接返回成功
        return ConfigUpdateResult.builder()
                .success(true)
                .changeId(changeId)
                .requiresApproval(false)
                .build();
    }

    @Override
    public UserConfigView rollback(String userId, String targetVersion) {
        ConfigSnapshot targetSnapshot = snapshots.stream()
                .filter(s -> s.getVersion().equals(targetVersion))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + targetVersion));
        currentConfig.clear();
        currentConfig.putAll(targetSnapshot.getConfig());
        saveCurrentConfig();
        log.info("Config rolled back to version {} by user {}", targetVersion, userId);
        return getUserConfig(userId);
    }

    @Override
    public List<ConfigFieldMeta> getConfigMetadata() {
        // 返回硬编码的元数据，也可以从配置文件读取
        return List.of(
                ConfigFieldMeta.builder()
                        .key("promengine.mode")
                        .displayName("运行模式")
                        .description("硅基模式(silicon)或碳基模式(carbon)")
                        .type(ConfigFieldMeta.ConfigType.ENUM)
                        .userModifiable(true)
                        .requiresApproval(true)
                        .requiresRestart(true)
                        .defaultValue("carbon")
                        .currentValue(currentConfig.get("promengine.mode"))
                        .constraints(Map.of("options", List.of("silicon", "carbon")))
                        .build(),
                ConfigFieldMeta.builder()
                        .key("promengine.cognition.focus-mode")
                        .displayName("专注模式")
                        .type(ConfigFieldMeta.ConfigType.STRING)
                        .userModifiable(true)
                        .requiresApproval(false)
                        .defaultValue("auto")
                        .currentValue(currentConfig.get("promengine.cognition.focus-mode"))
                        .build()
                // 可根据需要添加更多配置元数据...
        );
    }

    // ---------- 持久化辅助方法 ----------
    private void loadCurrentConfig() {
        File file = CONFIG_FILE_PATH.toFile();
        if (file.exists()) {
            try {
                Map<String, Object> loaded = objectMapper.readValue(file, new TypeReference<Map<String, Object>>() {});
                currentConfig.putAll(loaded);
            } catch (IOException e) {
                log.error("Failed to load current config", e);
            }
        }
    }

    private void saveCurrentConfig() {
        try {
            objectMapper.writeValue(CONFIG_FILE_PATH.toFile(), currentConfig);
        } catch (IOException e) {
            log.error("Failed to save current config", e);
        }
    }

    private void loadSnapshots() {
        File[] files = SNAPSHOT_DIR.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;
        for (File file : files) {
            try {
                ConfigSnapshot snapshot = objectMapper.readValue(file, ConfigSnapshot.class);
                snapshots.add(snapshot);
            } catch (IOException e) {
                log.warn("Failed to load snapshot {}", file, e);
            }
        }
        snapshots.sort(Comparator.comparing(ConfigSnapshot::getTimestamp).reversed());
    }

    private void saveSnapshot(ConfigSnapshot snapshot) {
        try {
            Path filePath = SNAPSHOT_DIR.resolve(snapshot.getVersion() + ".json");
            objectMapper.writeValue(filePath.toFile(), snapshot);
        } catch (IOException e) {
            log.error("Failed to save snapshot {}", snapshot.getVersion(), e);
        }
    }

    // ---------- 内部数据类 ----------
    @lombok.Builder
    @lombok.Data
    static class ConfigSnapshot {
        private String version;
        private Instant timestamp;
        private Map<String, Object> config;
    }

    @lombok.Builder
    @lombok.Data
    static class ConfigChangeRecord {
        private String changeId;
        private String userId;
        private Instant timestamp;
        private Map<String, Object> updates;
        private String previousVersion;
    }
}