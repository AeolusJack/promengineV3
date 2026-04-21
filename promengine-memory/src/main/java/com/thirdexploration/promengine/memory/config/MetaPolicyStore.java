package com.thirdexploration.promengine.memory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Aeon 记忆系统
 * 元策略持久化存储。
 * 策略数据以 JSON 格式存储在文件系统中，启动时加载，支持运行时热更新。
 * 代码中不包含任何硬编码默认值，完全由外部文件驱动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetaPolicyStore {

    private final ObjectMapper objectMapper;
    private final AeonMemoryProperties properties;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private MetaPolicyData data;

    @PostConstruct
    public void init() {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    /**
     * 从文件加载策略数据。若文件不存在，创建空结构并持久化。
     */
    public void load() {
        lock.writeLock().lock();
        try {
            Path path = Paths.get(properties.getMetaPolicyPath());
            if (Files.exists(path)) {
                String json = Files.readString(path);
                data = objectMapper.readValue(json, MetaPolicyData.class);
                log.info("Loaded meta policies from {}, version={}, domains={}, layers={}, sharingLevels={}",
                        path, data.getVersion(),
                        data.getDomains().size(),
                        data.getLayers().size(),
                        data.getSharingLevels().size());
            } else {
                // 创建空结构，不填充任何默认值
                data = new MetaPolicyData();
                data.setVersion(1);
                data.setDomains(new ArrayList<>());
                data.setLayers(new ArrayList<>());
                data.setSharingLevels(new ArrayList<>());
                persist();
                log.warn("Meta policies file not found at {}. Created empty structure. " +
                        "Please initialize domains/layers via API or manually edit the JSON file.", path);
            }
        } catch (IOException e) {
            log.error("Failed to load meta policies", e);
            data = new MetaPolicyData();
            data.setVersion(1);
            data.setDomains(new ArrayList<>());
            data.setLayers(new ArrayList<>());
            data.setSharingLevels(new ArrayList<>());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 持久化当前策略数据到文件。
     */
    public void persist() {
        lock.readLock().lock();
        try {
            Path path = Paths.get(properties.getMetaPolicyPath());
            Files.createDirectories(path.getParent());
            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
            objectMapper.writeValue(tempPath.toFile(), data);
            Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Persisted meta policies to {}", path);
        } catch (IOException e) {
            log.error("Failed to persist meta policies", e);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取当前策略数据的深拷贝。
     */
    public MetaPolicyData getData() {
        lock.readLock().lock();
        try {
            return data.copy();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 更新策略数据并持久化，版本号自动递增。
     */
    public void updateData(MetaPolicyData newData) {
        lock.writeLock().lock();
        try {
            this.data = newData.copy();
            this.data.setVersion(this.data.getVersion() + 1);
            persist();
            log.info("Updated meta policies, new version: {}", this.data.getVersion());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 元策略数据容器。
     */
    @lombok.Data
    public static class MetaPolicyData {
        private int version;
        private List<DomainDef> domains = new ArrayList<>();
        private List<LayerDef> layers = new ArrayList<>();
        private List<SharingLevelDef> sharingLevels = new ArrayList<>();

        public MetaPolicyData copy() {
            MetaPolicyData copy = new MetaPolicyData();
            copy.setVersion(this.version);
            copy.setDomains(new ArrayList<>(this.domains));
            copy.setLayers(new ArrayList<>(this.layers));
            copy.setSharingLevels(new ArrayList<>(this.sharingLevels));
            return copy;
        }

        @lombok.Data
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class DomainDef {
            private String name;
            private String description;
            private boolean isDefault;
            private List<String> allowedRoles;   // 允许访问的角色列表
        }

        @lombok.Data
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class LayerDef {
            private String name;
            private String description;
            private String ttl;          // 格式: "30m", "7d", null 表示永久
            private int maxCapacity;
            private double forgettingRate;
            private double reliabilityThreshold;   // 新增，过程记忆专用
        }

        @lombok.Data
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class SharingLevelDef {
            private String name;
            private String description;
        }
    }
}