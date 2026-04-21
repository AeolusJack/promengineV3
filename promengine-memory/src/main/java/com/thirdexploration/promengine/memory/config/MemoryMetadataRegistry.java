package com.thirdexploration.promengine.memory.config;

import com.thirdexploration.promengine.memory.config.MetaPolicyStore.MetaPolicyData;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aeon 记忆系统
 * 内存中的元策略注册表，提供快速查询和校验。
 * 从 MetaPolicyStore 加载数据，支持热刷新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryMetadataRegistry {

    private final MetaPolicyStore policyStore;

    private final Map<String, MetaPolicyData.DomainDef> domains = new ConcurrentHashMap<>();
    private final Map<String, MetaPolicyData.LayerDef> layers = new ConcurrentHashMap<>();
    private final Map<String, MetaPolicyData.SharingLevelDef> sharingLevels = new ConcurrentHashMap<>();

    private volatile String defaultDomain;
    private volatile String defaultLayer = "episodic";
    private volatile String defaultSharingLevel = "private";

    @PostConstruct
    public void init() {
        refresh();
    }

    public MetaPolicyData.LayerDef getLayerConfig(String layer) {
        return layers.get(layer);
    }
    /**
     * 从持久化存储刷新内存注册表。
     */
    public void refresh() {
        MetaPolicyData data = policyStore.getData();
        domains.clear();
        layers.clear();
        sharingLevels.clear();

        for (MetaPolicyData.DomainDef d : data.getDomains()) {
            domains.put(d.getName(), d);
            if (d.isDefault()) {
                defaultDomain = d.getName();
            }
        }
        for (MetaPolicyData.LayerDef l : data.getLayers()) {
            layers.put(l.getName(), l);
        }
        for (MetaPolicyData.SharingLevelDef s : data.getSharingLevels()) {
            sharingLevels.put(s.getName(), s);
        }

        // 如果未指定默认域，选择第一个
        if (defaultDomain == null && !domains.isEmpty()) {
            defaultDomain = domains.keySet().iterator().next();
        }

        log.info("MemoryMetadataRegistry refreshed: domains={}, layers={}, sharingLevels={}",
                domains.keySet(), layers.keySet(), sharingLevels.keySet());
    }



    public Set<String> getRegisteredDomains() {
        return domains.keySet();
    }

    public Set<String> getRegisteredLayers() {
        return layers.keySet();
    }

    public Set<String> getRegisteredSharingLevels() {
        return sharingLevels.keySet();
    }

    public boolean isValidDomain(String domain) {
        return domain != null && domains.containsKey(domain);
    }

    public boolean isValidLayer(String layer) {
        return layer != null && layers.containsKey(layer);
    }

    public boolean isValidSharingLevel(String level) {
        return level != null && sharingLevels.containsKey(level);
    }

    /**
     * 获取指定层级的 TTL，若未配置则返回 null。
     */
    public Duration getLayerTTL(String layer) {
        MetaPolicyData.LayerDef def = layers.get(layer);
        if (def == null || def.getTtl() == null) {
            return null;
        }
        // 解析如 "30m", "7d" 格式
        String ttlStr = def.getTtl().trim().toUpperCase();
        if (ttlStr.endsWith("M")) {
            return Duration.ofMinutes(Long.parseLong(ttlStr.substring(0, ttlStr.length() - 1)));
        } else if (ttlStr.endsWith("H")) {
            return Duration.ofHours(Long.parseLong(ttlStr.substring(0, ttlStr.length() - 1)));
        } else if (ttlStr.endsWith("D")) {
            return Duration.ofDays(Long.parseLong(ttlStr.substring(0, ttlStr.length() - 1)));
        } else {
            return Duration.parse("PT" + ttlStr);
        }
    }

    public int getLayerMaxCapacity(String layer) {
        MetaPolicyData.LayerDef def = layers.get(layer);
        return def != null ? def.getMaxCapacity() : 10000;
    }

    public double getLayerForgettingRate(String layer) {
        MetaPolicyData.LayerDef def = layers.get(layer);
        return def != null ? def.getForgettingRate() : 0.1;
    }

    public String getDefaultDomain() {
        return defaultDomain;
    }

    public String getDefaultLayer() {
        return defaultLayer;
    }

    public String getDefaultSharingLevel() {
        return defaultSharingLevel;
    }

    /**
     * 管理 API 调用此方法更新策略并刷新注册表。
     */
    public void updatePolicy(MetaPolicyData newData) {
        policyStore.updateData(newData);
        refresh();
    }
}