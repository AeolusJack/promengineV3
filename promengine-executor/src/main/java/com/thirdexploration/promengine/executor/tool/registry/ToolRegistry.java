package com.thirdexploration.promengine.executor.tool.registry;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thirdexploration.promengine.core.ToolInfoProvider;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ToolRegistry implements ToolInfoProvider {

    private final Map<String, SortedMap<String, RegisteredTool>> tools = new ConcurrentHashMap<>();
    private final Cache<String, GrayscaleConfig> grayscaleCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    private final Map<String, ToolStats> statsMap = new ConcurrentHashMap<>();

    // 缓存列表，使用读写锁保护
    private volatile List<ToolCallback> cachedCallbacks;
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();


    public List<ToolCallback> getAllToolCallbacks() {
        // 先尝试读锁获取缓存
        cacheLock.readLock().lock();
        try {
            if (cachedCallbacks != null) {
                return cachedCallbacks;
            }
        } finally {
            cacheLock.readLock().unlock();
        }

        // 缓存为空，加写锁重建
        cacheLock.writeLock().lock();
        try {
            if (cachedCallbacks == null) { // 双重检查
                refreshCachedCallbacks();
            }
            return cachedCallbacks;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    private void refreshCachedCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (String name : tools.keySet()) {
            // resolve 已保证每个名称只返回当前激活版本
            resolve(name, null).ifPresent(tool -> callbacks.add(tool.toToolCallback()));
        }
        this.cachedCallbacks = Collections.unmodifiableList(callbacks);
        log.info("Refreshed tool callbacks cache, total: {}", callbacks.size());
    }

    public void register(ToolDefinition definition, ToolInvoker invoker) {
        String name = definition.getName();
        String version = definition.getVersion();
        cacheLock.writeLock().lock();
        try {
            SortedMap<String, RegisteredTool> versions = tools.computeIfAbsent(name, k -> new TreeMap<>());
            if (versions.containsKey(version)) {
                log.warn("Tool '{}' version '{}' already registered, skipping", name, version);
                return;
            }
            versions.put(version, new RegisteredTool(definition, invoker));
            statsMap.putIfAbsent(name, new ToolStats());
            // 清空缓存，下次调用时重建
            this.cachedCallbacks = null;
        } finally {
            cacheLock.writeLock().unlock();
        }
        log.debug("Registered tool: {} version {}", name, version);
    }

    public Optional<RegisteredTool> resolve(String toolName, String requestedVersion) {
        SortedMap<String, RegisteredTool> versions = tools.get(toolName);
        if (versions == null) return Optional.empty();

        GrayscaleConfig config = grayscaleCache.get(toolName, this::loadGrayscaleConfig);
        if (config != null && config.isEnabled()) {
            double dice = Math.random();
            if (dice < config.getTrafficRatio()) {
                RegisteredTool newTool = versions.get(config.getNewVersion());
                if (newTool != null) {
                    statsMap.get(toolName).incrementCall(config.getNewVersion());
                    return Optional.of(newTool);
                }
            }
        }

        if (requestedVersion != null) {
            RegisteredTool tool = versions.get(requestedVersion);
            if (tool != null) {
                statsMap.get(toolName).incrementCall(requestedVersion);
                return Optional.of(tool);
            }
        }

        String latestVersion = versions.lastKey();
        RegisteredTool latest = versions.get(latestVersion);
        statsMap.get(toolName).incrementCall(latestVersion);
        return Optional.of(latest);
    }

    public Set<String> getRegisteredToolNames() {
        return tools.keySet();
    }

    private GrayscaleConfig loadGrayscaleConfig(String toolName) {
        return null;
    }

    // ---------- ToolInfoProvider 实现 ----------
    @Override
    public List<ToolInfo> getAvailableTools() {
        return getAllToolCallbacks().stream()
                .map(tc -> new ToolInfo(tc.getName(), tc.getDescription()))
                .toList();
    }

    @Override
    public List<String> getAvailableToolNames() {
        return getAllToolCallbacks().stream()
                .map(ToolCallback::getName)
                .toList();
    }

    @Override
    public String getToolDescriptions() {
        return getAllToolCallbacks().stream()
                .map(tc -> "- " + tc.getName() + ": " + tc.getDescription())
                .collect(Collectors.joining("\n"));
    }

    // ---------- 内部类 ----------
    @Data
    public static class GrayscaleConfig {
        private boolean enabled;
        private String newVersion;
        private double trafficRatio;
    }

    @Data
    public static class ToolStats {
        private final AtomicLong totalCalls = new AtomicLong();
        private final AtomicLong successCalls = new AtomicLong();
        private final Map<String, AtomicLong> versionCalls = new ConcurrentHashMap<>();

        void incrementCall(String version) {
            totalCalls.incrementAndGet();
            versionCalls.computeIfAbsent(version, k -> new AtomicLong()).incrementAndGet();
        }
    }

    public record RegisteredTool(ToolDefinition definition, ToolInvoker invoker) {
        public ToolCallback toToolCallback() {
            return new ToolCallback() {
                @Override
                public String getName() {
                    return definition.getName();
                }

                @Override
                public String getDescription() {
                    return definition.getDescription();
                }

                @Override
                public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                    return org.springframework.ai.tool.definition.ToolDefinition.builder()
                            .name(definition.getName())
                            .description(definition.getDescription())
                            .inputSchema(definition.toJsonSchema())
                            .build();
                }

                @Override
                public String call(String input) {
                    Map<String, Object> args;
                    try {
                        args = new com.fasterxml.jackson.databind.ObjectMapper().readValue(input, Map.class);
                    } catch (Exception e) {
                        return "参数解析失败：" + e.getMessage();
                    }
                    try {
                        return invoker.invoke(args);
                    } catch (Exception e) {
                        return "工具执行失败：" + e.getMessage();
                    }
                }

                @Override
                public String call(String toolInput, ToolContext toolContext) {
                    return call(toolInput);
                }
            };
        }
    }

    @FunctionalInterface
    public interface ToolInvoker {
        String invoke(Map<String, Object> args) throws Exception;
    }
}