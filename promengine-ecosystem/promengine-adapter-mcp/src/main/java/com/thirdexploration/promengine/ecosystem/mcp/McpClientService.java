package com.thirdexploration.promengine.ecosystem.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.registry.ToolDefinition;
import com.thirdexploration.promengine.executor.tool.registry.ToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

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
    private final ObjectMapper objectMapper;
    private final McpBuiltinProperties builtinProperties; // 新的配置绑定类

    /** 活跃客户端：serverId -> McpSyncClient */
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();

    /**
     * 应用完全启动后，安装内置服务器并连接所有已启用的服务器。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        installBuiltinServers();
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
     * 连接指定 MCP 服务器，获取工具并注册到 ToolRegistry。
     */
    public void connectServer(McpServerRecord server) {


        if (clients.containsKey(server.getId())) {
            disconnectServer(server.getId());
        }
        try {
            McpSyncClient client = buildMcpClient(server);
            client.initialize();
            clients.put(server.getId(), client);

            // 发送 initialized 通知（标准 MCP 流程）
            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "notifications/initialized");
            // 通过客户端的底层发送通知（需获取 transport）
            // 简便方式：使用 client 的请求方法发送一个不带 id 的请求
            // 由于 McpSyncClient 没有直接发送通知的方法，我们可以直接使用 HttpClient 发送
            // 但为了避免额外依赖，这里简单构造一个不带 id 的 JSON-RPC 请求

            List<McpSchema.Tool> tools = new ArrayList<>();
            String cursor = null;
            do {
                McpSchema.ListToolsResult result = client.listTools(cursor);
                tools.addAll(result.tools());
                cursor = result.nextCursor();
            } while (cursor != null && !cursor.isEmpty());


            for (McpSchema.Tool tool : tools) {
                String fullName = "mcp:" + server.getName() + ":" + tool.name();
                ToolDefinition definition = ToolDefinition.builder()
                        .name(fullName)
                        .description(tool.description() != null ? tool.description() : "")
                        .version("1.0.0")
                        .category(ToolHandler.Category.UTILITY)
                        .location(ToolHandler.Location.REMOTE)
                        .enabled(true)
                        .build();
                toolRegistry.registerMcpTool(definition, args -> {
                    McpSchema.CallToolRequest callRequest = new McpSchema.CallToolRequest(tool.name(), args);
                    McpSchema.CallToolResult result = client.callTool(callRequest);
                    // 优先取 structuredContent，否则 content
                    if (result.content() != null && !result.content().isEmpty()) {
                        return result.content().get(0).toString();
                    }
                    return result.toString();
                });
            }
            log.info("Connected to MCP server {} at {}, {} tools registered",
                    server.getName(), server.getUrl(), tools.size());
        } catch (Exception e) {
            log.error("Failed to connect to MCP server {}: {}", server.getName(), e.getMessage());
        }
    }

    /**
     * 断开指定 MCP 服务器，并移除已注册的工具。
     */
    public void disconnectServer(String serverId) {
        McpSyncClient client = clients.remove(serverId);
        if (client != null) {
            try {
                client.closeGracefully();
            } catch (Exception e) {
                log.warn("Error closing MCP client for server {}", serverId, e);
            }
            toolRegistry.removeMcpTools(serverId);
        }
    }

    /**
     * 获取所有已连接的 MCP 服务器信息。
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
     * 添加新的 MCP 服务器（简易版本，兼容原有前端调用）。
     */
    public McpServerRecord addServer(String name, String url) {
        return addServer(name, url, null, "sse", Collections.emptyMap());
    }

    /**
     * 添加新的 MCP 服务器（完整参数），持久化并自动连接。
     */
    public McpServerRecord addServer(String name, String url, String authToken,
                                     String transport, Map<String, String> headers) {
        McpServerRecord record = McpServerRecord.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .url(url)
                .enabled(true)
                .createdAt(System.currentTimeMillis())
                .authToken(authToken)
                .transport(transport != null ? transport : "sse")
                .headers(headers != null ? new HashMap<>(headers) : new HashMap<>())
                .build();
        repository.save(record);
        connectServer(record);
        return record;
    }

    /**
     * 删除 MCP 服务器（断开连接并移除持久化记录）。
     */
    public void removeServer(String serverId) {
        disconnectServer(serverId);
        repository.deleteById(serverId);
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 若内置服务器不存在，则自动创建并保存到数据库。
     */
    private void installBuiltinServers() {
        List<McpBuiltinProperties.BuiltinServerConfig> configs = builtinProperties.getBuiltinServers();
        if (configs == null || configs.isEmpty()) {
            return;
        }
        for (McpBuiltinProperties.BuiltinServerConfig cfg : configs) {
            if (repository.findById(cfg.getId()) == null) {
                McpServerRecord record = McpServerRecord.builder()
                        .id(cfg.getId())
                        .name(cfg.getName())
                        .url(cfg.getUrl())
                        .enabled(cfg.isEnabled())
                        .createdAt(System.currentTimeMillis())
                        .authToken(cfg.getAuthToken())
                        .transport(cfg.getTransport())
                        .command(cfg.getCommand())
                        .args(cfg.getArgs())
                        .headers(cfg.getHeaders() != null ? new HashMap<>(cfg.getHeaders()) : new HashMap<>())
                        .build();
                repository.save(record);
                log.info("Installed builtin MCP server: {}", cfg.getName());
            }
        }
    }

    /**
     * 根据服务器配置构建对应的 MCP 客户端。当前仅支持 SSE。
     */
    private McpSyncClient buildMcpClient(McpServerRecord server) {
        String transport = server.getTransport() != null ? server.getTransport() : "sse";
        if ("stdio".equalsIgnoreCase(transport)) {
            throw new UnsupportedOperationException("stdio transport not yet implemented");
        }
        return buildSseClient(server);
    }

    /**
     * 构建 SSE（Streamable HTTP）客户端，自动注入请求头。
     */
    private McpSyncClient buildSseClient(McpServerRecord server) {
        // 1. 解析 URL
        java.net.URI uri = java.net.URI.create(server.getUrl());
        String baseUrl = uri.getScheme() + "://" + uri.getAuthority();
        String endpoint = uri.getPath();
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = "/mcp";
        }

        // 2. 构建 HTTP 客户端与请求构建器
        java.net.http.HttpClient.Builder httpClientBuilder = java.net.http.HttpClient.newBuilder();
        java.net.http.HttpRequest.Builder requestBuilder = java.net.http.HttpRequest.newBuilder();

        // 3. 设置请求头（自定义头 + 认证 + 协议版本）
        if (server.getHeaders() != null) {
            server.getHeaders().forEach(requestBuilder::header);
        }
        if (server.getAuthToken() != null && !server.getAuthToken().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + server.getAuthToken());
        }
        requestBuilder.header("MCP-Protocol-Version", "2025-11-25");

        // 4. 创建传输客户端
        HttpClientSseClientTransport transport = new HttpClientSseClientTransport(
                httpClientBuilder,
                requestBuilder,
                baseUrl,
                endpoint,
                objectMapper
        );

        // 5. 构建同步 MCP 客户端
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .build();
    }
}