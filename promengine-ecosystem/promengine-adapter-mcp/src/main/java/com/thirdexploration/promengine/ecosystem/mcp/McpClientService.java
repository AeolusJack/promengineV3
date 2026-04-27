package com.thirdexploration.promengine.ecosystem.mcp;

import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.registry.ToolDefinition;
import com.thirdexploration.promengine.executor.tool.registry.ToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientService {

    private final McpServerRepository repository;
    private final ToolRegistry toolRegistry;
    // 活跃客户端：serverId -> McpSyncClient
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();

//    @PostConstruct
    /**
     * 应用完全启动后，连接所有已启用的 MCP 服务器
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        List<McpServerRecord> servers = repository.findAll();
        for (McpServerRecord server : servers) {
            if (server.isEnabled()) {
                try {
                    connectServer(server);
                } catch (Exception e) {
                    log.error("Failed to connect MCP server {} on startup", server.getName(), e);
                }
            }
        }
    }

    @PreDestroy
    public void destroy() {
        clients.keySet().forEach(this::disconnectServer);
    }

    /**
     * 连接指定 MCP 服务器，获取工具并注册到 ToolRegistry
     */
    public void connectServer(McpServerRecord server) {
        if (clients.containsKey(server.getId())) {
            disconnectServer(server.getId());
        }
        try {
            // 1. 创建 SSE 传输客户端
            HttpClientSseClientTransport transport = HttpClientSseClientTransport
                    .builder(server.getUrl())
                    .build();

            // 2. 构建同步 MCP 客户端
            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(30))
                    .build();

            // 3. 初始化握手
            client.initialize();
            clients.put(server.getId(), client);

            // 4. 获取工具列表并注册
            McpSchema.ListToolsResult toolsResult = client.listTools();
            List<McpSchema.Tool> tools = toolsResult.tools();

            for (McpSchema.Tool tool : tools) {
                // 命名规则：mcp:<服务名>:<原始工具名>
                String fullName = "mcp:" + server.getName() + ":" + tool.name();
                ToolDefinition definition = ToolDefinition.builder()
                        .name(fullName)
                        .description(tool.description() != null ? tool.description() : "")
                        .version("1.0.0")
                        .category(ToolHandler.Category.UTILITY)
                        .location(ToolHandler.Location.REMOTE)
                        .enabled(true)
                        .build();
                // 注册工具，并提供调用器
                toolRegistry.registerMcpTool(definition, args -> {
                    McpSchema.CallToolRequest callRequest = new McpSchema.CallToolRequest(tool.name(), args);
                    McpSchema.CallToolResult result = client.callTool(callRequest);
                    // 将结果转换为字符串（取第一个文本内容）
                    if (result.content() != null && !result.content().isEmpty()) {
                        return result.content().get(0).toString();
                    }
                    return result.toString();
                });
            }
            log.info("Connected to MCP server {} at {}, {} tools registered", server.getName(), server.getUrl(), tools.size());
        } catch (Exception e) {
            log.error("Failed to connect to MCP server {}: {}", server.getName(), e.getMessage());
        }
    }

    /**
     * 断开指定 MCP 服务器，并移除已注册的工具
     */
    public void disconnectServer(String serverId) {
        McpSyncClient client = clients.remove(serverId);
        if (client != null) {
            try {
                client.closeGracefully();
            } catch (Exception e) {
                log.warn("Error closing MCP client for server {}", serverId, e);
            }
            // 移除该服务器注册的所有工具
            toolRegistry.removeMcpTools(serverId);
        }
    }

    /**
     * 获取所有已连接的 MCP 服务器信息
     */
    public List<Map<String, Object>> getAllServers() {
        List<McpServerRecord> records = repository.findAll();
        return records.stream().map(record -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", record.getId());
            map.put("name", record.getName());
            map.put("url", record.getUrl());
            map.put("enabled", record.isEnabled());
            map.put("connected", clients.containsKey(record.getId()));
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 添加新的 MCP 服务器（持久化并自动连接）
     */
    public McpServerRecord addServer(String name, String url) {
        McpServerRecord record = McpServerRecord.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .url(url)
                .enabled(true)
                .createdAt(System.currentTimeMillis())
                .build();
        repository.save(record);
        connectServer(record);
        return record;
    }

    /**
     * 删除 MCP 服务器（断开连接并移除持久化记录）
     */
    public void removeServer(String serverId) {
        disconnectServer(serverId);
        repository.deleteById(serverId);
    }
}