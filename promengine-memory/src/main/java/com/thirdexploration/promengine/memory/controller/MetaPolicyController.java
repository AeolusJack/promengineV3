package com.thirdexploration.promengine.memory.controller;

import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.config.MetaPolicyStore;
import com.thirdexploration.promengine.memory.config.MetaPolicyStore.MetaPolicyData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 元策略管理 REST API。
 * 提供对记忆域、层级、共享级别的增删改查及热重载功能。
 */
@RestController
@RequestMapping("/api/v1/memory/meta-policies")
@RequiredArgsConstructor
public class MetaPolicyController {

    private final MetaPolicyStore policyStore;
    private final MemoryMetadataRegistry registry;

    /**
     * 获取当前完整策略配置。
     */
    @GetMapping
    public MetaPolicyData getCurrentPolicies() {
        return policyStore.getData();
    }

    /**
     * 全量替换策略配置。
     */
    @PutMapping
    public Map<String, Object> updatePolicies(@RequestBody MetaPolicyData newData) {
        policyStore.updateData(newData);
        registry.refresh();
        return Map.of("success", true, "version", policyStore.getData().getVersion());
    }

    /**
     * 从文件重新加载策略（当手动编辑 JSON 文件后调用）。
     */
    @PostMapping("/reload")
    public Map<String, Object> reloadFromFile() {
        policyStore.load();
        registry.refresh();
        return Map.of("success", true, "version", policyStore.getData().getVersion());
    }

    // ---------- 域管理 ----------
    @GetMapping("/domains")
    public Set<String> listDomains() {
        return registry.getRegisteredDomains();
    }

    @PostMapping("/domains")
    public Map<String, Object> addDomain(@RequestBody MetaPolicyData.DomainDef domain) {
        MetaPolicyData data = policyStore.getData();
        boolean exists = data.getDomains().stream().anyMatch(d -> d.getName().equals(domain.getName()));
        if (exists) {
            return Map.of("success", false, "message", "Domain already exists: " + domain.getName());
        }
        if (domain.isDefault()) {
            data.getDomains().forEach(d -> d.setDefault(false));
        }
        data.getDomains().add(domain);
        registry.updatePolicy(data);
        return Map.of("success", true, "domain", domain.getName());
    }

    @DeleteMapping("/domains/{name}")
    public Map<String, Object> removeDomain(@PathVariable String name) {
        MetaPolicyData data = policyStore.getData();
        boolean removed = data.getDomains().removeIf(d -> d.getName().equals(name));
        if (removed) {
            registry.updatePolicy(data);
        }
        return Map.of("success", removed);
    }

    // ---------- 层级管理 ----------
    @GetMapping("/layers")
    public Set<String> listLayers() {
        return registry.getRegisteredLayers();
    }

    @PostMapping("/layers")
    public Map<String, Object> addLayer(@RequestBody MetaPolicyData.LayerDef layer) {
        MetaPolicyData data = policyStore.getData();
        boolean exists = data.getLayers().stream().anyMatch(l -> l.getName().equals(layer.getName()));
        if (exists) {
            return Map.of("success", false, "message", "Layer already exists: " + layer.getName());
        }
        data.getLayers().add(layer);
        registry.updatePolicy(data);
        return Map.of("success", true, "layer", layer.getName());
    }

    @DeleteMapping("/layers/{name}")
    public Map<String, Object> removeLayer(@PathVariable String name) {
        MetaPolicyData data = policyStore.getData();
        boolean removed = data.getLayers().removeIf(l -> l.getName().equals(name));
        if (removed) {
            registry.updatePolicy(data);
        }
        return Map.of("success", removed);
    }

    // ---------- 共享级别管理 ----------
    @GetMapping("/sharing-levels")
    public Set<String> listSharingLevels() {
        return registry.getRegisteredSharingLevels();
    }

    @PostMapping("/sharing-levels")
    public Map<String, Object> addSharingLevel(@RequestBody MetaPolicyData.SharingLevelDef level) {
        MetaPolicyData data = policyStore.getData();
        boolean exists = data.getSharingLevels().stream().anyMatch(s -> s.getName().equals(level.getName()));
        if (exists) {
            return Map.of("success", false, "message", "Sharing level already exists: " + level.getName());
        }
        data.getSharingLevels().add(level);
        registry.updatePolicy(data);
        return Map.of("success", true, "level", level.getName());
    }

    @DeleteMapping("/sharing-levels/{name}")
    public Map<String, Object> removeSharingLevel(@PathVariable String name) {
        MetaPolicyData data = policyStore.getData();
        boolean removed = data.getSharingLevels().removeIf(s -> s.getName().equals(name));
        if (removed) {
            registry.updatePolicy(data);
        }
        return Map.of("success", removed);
    }
}