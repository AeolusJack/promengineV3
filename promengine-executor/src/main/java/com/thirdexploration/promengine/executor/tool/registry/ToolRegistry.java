package com.thirdexploration.promengine.executor.tool.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thirdexploration.promengine.core.AgentConfig;
import com.thirdexploration.promengine.core.ToolInfoProvider;
import com.thirdexploration.promengine.core.agent.AgentConfigProvider;
import com.thirdexploration.promengine.core.tenant.TenantContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ToolRegistry implements ToolInfoProvider {

    private final AgentConfigProvider agentConfigProvider;

    // 工具存储：name -> SortedMap<version, RegisteredTool>
    private final Map<String, SortedMap<String, RegisteredTool>> tools = new ConcurrentHashMap<>();

    // 灰度配置缓存
    private final Cache<String, GrayscaleConfig> grayscaleCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    // 调用统计
    private final Map<String, ToolStats> statsMap = new ConcurrentHashMap<>();

    // 已发布到市场的工具名称集合（仅作快速判断，实际以 ToolDefinition.published 为准）
    // 不再单独维护，直接查询定义

    // MCP 服务器关联的工具（用于批量移除）
    private final Map<String, Set<String>> mcpServerTools = new ConcurrentHashMap<>();

    // 缓存的 ToolCallback 列表（所有工具，供 Spring AI 使用）
    private volatile List<ToolCallback> cachedCallbacks;
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

    /**
     * 根据 Agent 配置过滤工具（返回该 Agent 允许的工具列表）
     */
    public List<ToolCallback> getToolsForAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return getAllToolCallbacks();
        }
        AgentConfig config = agentConfigProvider.getConfig(agentId);
        if (config == null || config.getTools() == null || config.getTools().isEmpty()) {
            return getAllToolCallbacks();
        }
        // 过滤出 Agent 允许的工具名
        Set<String> allowedNames = new HashSet<>(config.getTools());
        return getAllToolCallbacks().stream()
                .filter(tc -> allowedNames.contains(tc.getName()))
                .collect(Collectors.toList());
    }

    /**
     * 注册一个租户自定义工具（如 MCP 动态工具），使用当前租户ID
     */
    public void registerTenantTool(ToolDefinition definition, ToolInvoker invoker) {
        definition.setTenantId(TenantContext.getOrDefault());
        definition.setPublished(false);
        register(definition, invoker);
    }

    /**
     * 注册 MCP 工具（属于某个 MCP 服务器）
     */
    public void registerMcpTool(ToolDefinition definition, ToolInvoker invoker) {
        // MCP 工具可视为系统级或服务级，tenantId 由调用方指定（通常为 "system" 或空）
        if (definition.getTenantId() == null) {
            definition.setTenantId("system");
        }
        String[] parts = definition.getName().split(":", 3);
        if (parts.length >= 2) {
            String serverName = parts[1];
            mcpServerTools.computeIfAbsent(serverName, k -> ConcurrentHashMap.newKeySet())
                    .add(definition.getName());
        }
        register(definition, invoker);
    }

    /**
     * 移除指定 MCP 服务器的所有工具
     */
    public void removeMcpTools(String serverName) {
        Set<String> toolNames = mcpServerTools.remove(serverName);
        if (toolNames != null) {
            for (String name : toolNames) {
                tools.remove(name);
            }
            refreshCachedCallbacks();
            log.info("Removed {} MCP tools for server {}", toolNames.size(), serverName);
        }
    }

    /**
     * 获取所有可用工具的 ToolCallback（全局，包括系统和所有租户）
     */
    public List<ToolCallback> getAllToolCallbacks() {
        cacheLock.readLock().lock();
        try {
            if (cachedCallbacks != null) {
                return cachedCallbacks;
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        cacheLock.writeLock().lock();
        try {
            if (cachedCallbacks == null) {
                refreshCachedCallbacks();
            }
            return cachedCallbacks;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * 获取当前租户可用的工具 Callback（系统工具 + 当前租户工具）
     */
    public List<ToolCallback> getTenantToolCallbacks() {
        String tenantId = TenantContext.getOrDefault();
        return getAllToolCallbacks().stream()
                .filter(tc -> {
                    ToolDefinition def = resolveDefinition(tc.getName());
                    if (def == null) return false;
                    return "system".equals(def.getTenantId()) || tenantId.equals(def.getTenantId());
                })
                .collect(Collectors.toList());
    }

    /**
     * 刷新缓存（重新扫描所有注册的工具）
     */
    public void refreshCachedCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (String name : tools.keySet()) {
            resolve(name, null).ifPresent(tool -> callbacks.add(tool.toToolCallback()));
        }
        this.cachedCallbacks = Collections.unmodifiableList(callbacks);
        log.info("Refreshed tool callbacks cache, total: {}", callbacks.size());
    }

    /**
     * 注册工具（内部使用）
     */
    void register(ToolDefinition definition, ToolInvoker invoker) {
        String name = definition.getName();
        String version = definition.getVersion();
        // 如果没有 tenantId，默认设为 system
        if (definition.getTenantId() == null) {
            definition.setTenantId("system");
        }
        cacheLock.writeLock().lock();
        try {
            SortedMap<String, RegisteredTool> versions = tools.computeIfAbsent(name, k -> new TreeMap<>());
            if (versions.containsKey(version)) {
                log.warn("Tool '{}' version '{}' already registered, skipping", name, version);
                return;
            }
            versions.put(version, new RegisteredTool(definition, invoker));
            statsMap.putIfAbsent(name, new ToolStats());
            this.cachedCallbacks = null;   // 置空，下次调用时重建
        } finally {
            cacheLock.writeLock().unlock();
        }
        log.debug("Registered tool: {} version {}", name, version);
    }

    /**
     * 解析工具（考虑灰度发布）
     */
    public Optional<RegisteredTool> resolve(String toolName, String requestedVersion) {
        SortedMap<String, RegisteredTool> versions = tools.get(toolName);
        if (versions == null) return Optional.empty();

        // 灰度发布逻辑
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
        // 默认返回最新版本
        String latestVersion = versions.lastKey();
        RegisteredTool latest = versions.get(latestVersion);
        statsMap.get(toolName).incrementCall(latestVersion);
        return Optional.of(latest);
    }

    /**
     * 获取注册的工具名称集合
     */
    public Set<String> getRegisteredToolNames() {
        return tools.keySet();
    }

    /**
     * 根据工具名获取 ToolDefinition（任意版本），用于查询发布状态等
     */
    private ToolDefinition resolveDefinition(String toolName) {
        return resolve(toolName, null)
                .map(rt -> rt.definition())
                .orElse(null);
    }

    // ========== ToolInfoProvider 实现（面向租户） ==========

    @Override
    public List<ToolInfo> getAvailableTools() {
        return getTenantToolCallbacks().stream()
                .map(tc -> new ToolInfo(tc.getName(), tc.getDescription()))
                .toList();
    }

    @Override
    public List<String> getAvailableToolNames() {
        return getTenantToolCallbacks().stream()
                .map(ToolCallback::getName)
                .toList();
    }

    @Override
    public String getToolDescriptions() {
        return getTenantToolCallbacks().stream()
                .map(tc -> "- " + tc.getName() + ": " + tc.getDescription())
                .collect(Collectors.joining("\n"));
    }

    // ========== 市场相关 ==========

    /**
     * 获取所有已发布到市场的工具（跨租户）
     */
    public List<ToolDefinition> listPublishedTools() {
        List<ToolDefinition> result = new ArrayList<>();
        for (String name : tools.keySet()) {
            resolve(name, null).ifPresent(rt -> {
                ToolDefinition def = rt.definition();
                if (def.isEnabled() && def.isPublished()) {
                    result.add(def);
                }
            });
        }
        return result;
    }

    /**
     * 发布工具到市场（仅当前租户拥有者可操作）
     */
    public void publishTool(String toolName, boolean publish) {
        String tenantId = TenantContext.getOrDefault();
        Optional<RegisteredTool> resolved = resolve(toolName, null);
        if (resolved.isEmpty()) {
            throw new NoSuchElementException("Tool not found: " + toolName);
        }
        ToolDefinition def = resolved.get().definition();
        if (!tenantId.equals(def.getTenantId())) {
            throw new SecurityException("Only the owning tenant can publish this tool");
        }
        def.setPublished(publish);
        refreshCachedCallbacks();
        log.info("Tool '{}' published status changed to {}", toolName, publish);
    }

    /**
     * 从市场安装工具到当前租户（实质是标记为租户可见？不，工具已存在，只是启用并复制）
     * 实际上，如果工具是系统工具，无需安装；如果是其他租户发布的，需要复制一份到当前租户。
     * 为了简化，这里提供一个安装方法：如果工具不属于当前租户，则创建一个副本并注册为当前租户。
     */
    public void installTool(String sourceToolName) {
        String targetTenantId = TenantContext.getOrDefault();
        Optional<RegisteredTool> resolved = resolve(sourceToolName, null);
        if (resolved.isEmpty()) {
            throw new NoSuchElementException("Source tool not found: " + sourceToolName);
        }
        RegisteredTool source = resolved.get();
        ToolDefinition sourceDef = source.definition();
        if (targetTenantId.equals(sourceDef.getTenantId())) {
            // 同租户，直接启用即可
            sourceDef.setEnabled(true);
            refreshCachedCallbacks();
            return;
        }
        // 跨租户复制
        ToolDefinition newDef = ToolDefinition.builder()
                .name(sourceDef.getName() + " (installed)")
                .description(sourceDef.getDescription())
                .version(sourceDef.getVersion())
                .category(sourceDef.getCategory())
                .location(sourceDef.getLocation())
                .enabled(true)
                .parameters(sourceDef.getParameters())
                .sandboxPolicy(sourceDef.getSandboxPolicy())
                .riskLevel(sourceDef.getRiskLevel())
                .requiredPermissions(sourceDef.getRequiredPermissions())
                .tenantId(targetTenantId)
                .published(false)
                .build();
        register(newDef, source.invoker());
        log.info("Tool '{}' installed from market to tenant '{}'", sourceToolName, targetTenantId);
    }

    // ========== 灰度发布内部方法 ==========

    private GrayscaleConfig loadGrayscaleConfig(String toolName) {
        // 暂未实现，可集成数据库配置
        return null;
    }

    // ========== 内部类 ==========

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
                        args = new ObjectMapper().readValue(input, Map.class);
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