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

@Slf4j
@Component
public class ToolRegistry implements ToolInfoProvider {

    // 工具名称 -> 版本列表 (使用字符串版本排序，可自定义比较器)
    private final Map<String, SortedMap<String, RegisteredTool>> tools = new ConcurrentHashMap<>();

    private final Cache<String, GrayscaleConfig> grayscaleCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    private final Map<String, ToolStats> statsMap = new ConcurrentHashMap<>();

    public void register(ToolDefinition definition, ToolInvoker invoker) {
        String version = definition.getVersion();
        RegisteredTool tool = new RegisteredTool(definition, invoker);
        tools.computeIfAbsent(definition.getName(), k -> new TreeMap<>())
                .put(version, tool);
        statsMap.putIfAbsent(definition.getName(), new ToolStats());
        log.debug("Registered tool: {} version {}", definition.getName(), version);
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
    public List<ToolCallback> getAllToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (String name : tools.keySet()) {
            resolve(name, null).ifPresent(tool -> {
                callbacks.add(tool.toToolCallback());
                log.debug("Adding tool callback: {}", tool.definition().getName());
            });
        }
        log.info("Total tool callbacks prepared: {}", callbacks.size());
        return callbacks;
    }

    private GrayscaleConfig loadGrayscaleConfig(String toolName) {
        return null;
    }

    @Override
    public List<ToolInfo> getAvailableTools() {
        return getAllToolCallbacks().stream()
                .map(tc -> new ToolInfo(tc.getName(), tc.getDescription()))
                .toList();
    }

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
        // 替换原先的 toToolCallback 方法
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
                    // 将我们的 ToolDefinition 转换为 Spring AI 需要的 ToolDefinition
                    return org.springframework.ai.tool.definition.ToolDefinition.builder()
                            .name(definition.getName())
                            .description(definition.getDescription())
                            .inputSchema(definition.toJsonSchema())   // 需要在 ToolDefinition 中实现 toJsonSchema()
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
                    // M7 默认实现会调用 call(String)，直接委托即可
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