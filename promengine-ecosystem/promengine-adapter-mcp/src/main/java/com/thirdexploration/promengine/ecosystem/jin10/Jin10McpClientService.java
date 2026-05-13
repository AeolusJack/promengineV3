package com.thirdexploration.promengine.ecosystem.jin10;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 金十数据 MCP 客户端服务 —— 严格遵循 MCP 2025‑11‑25 Streamable HTTP 规范。
 *
 * <p>核心改动：请求头强制包含 {@code Accept: application/json, text/event-stream}，
 * 并完整解析服务器返回的 SSE 事件流，从中提取 JSON‑RPC 响应。</p>
 *
 * <p>协议流程：initialize → initialized 通知 → tools/list / tools/call</p>
 * <p>分页：请求参数 cursor，响应字段 next_cursor / has_more</p>
 * <p>结果读取：优先 structuredContent，content 仅作可读文本</p>
 */
@Slf4j
@Service
public class Jin10McpClientService {

    private final Jin10McpProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final AtomicInteger requestId = new AtomicInteger(1);
    private final AtomicReference<Boolean> initialized = new AtomicReference<>(false);
    private volatile String serverName;
    private volatile String serverVersion;

    private final Map<String, JsonNode> toolsCache = new ConcurrentHashMap<>();

    public Jin10McpClientService(Jin10McpProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    // ═══════════════════ 生命周期 ═══════════════════

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!properties.isEnabled()) {
            log.info("金十数据 MCP 服务未启用");
            return;
        }
        try {
            initialize();
            sendInitializedNotification();
            loadTools();
            log.info("金十数据 MCP 连接成功 (server={}, version={}), 已加载 {} 个工具",
                    serverName, serverVersion, toolsCache.size());
        } catch (Exception e) {
            log.error("金十数据 MCP 初始化失败: {}", e.getMessage());
        }
    }

    public void reconnect() {
        initialized.set(false);
        toolsCache.clear();
        init();
    }

    // ═══════════════════ MCP 协议方法 ═══════════════════

    private void initialize() throws IOException {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", properties.getProtocolVersion());
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "PromEngine‑Jin10");
        clientInfo.put("version", "3.0.0");
        params.set("clientInfo", clientInfo);

        JsonNode response = sendRequest("initialize", params);
        JsonNode result = response.get("result");
        if (result == null) {
            throw new RuntimeException("初始化失败：" + response);
        }
        serverName = result.path("serverInfo").path("name").asText("Jin10Server");
        serverVersion = result.path("serverInfo").path("version").asText("unknown");
        log.info("Initialize 成功, 协商协议版本={}, 服务器={}:{}",
                result.path("protocolVersion").asText(), serverName, serverVersion);
    }

    private void sendInitializedNotification() {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        sendNotification(notification);
        initialized.set(true);
    }

    private void loadTools() throws IOException {
        String cursor = null;
        boolean hasMore = true;
        while (hasMore) {
            ObjectNode params = objectMapper.createObjectNode();
            if (cursor != null) params.put("cursor", cursor);

            JsonNode response = sendRequest("tools/list", params);
            JsonNode result = response.get("result");
            if (result == null) break;

            JsonNode tools = result.get("tools");
            if (tools != null && tools.isArray()) {
                for (JsonNode tool : tools) {
                    String name = tool.get("name").asText();
                    toolsCache.put(name, tool);
                }
            }
            cursor = result.path("nextCursor").asText(null);
            hasMore = cursor != null && !cursor.isEmpty();
        }
    }

    // ═══════════════════ 工具调用（面向用户） ═══════════════════

    public Map<String, Object> getQuote(String code) throws IOException {
        return callToolAndParse("get_quote", Map.of("code", code));
    }

    public Map<String, Object> getKline(String code, String time, Integer count) throws IOException {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("code", code);
        if (time != null && !time.isEmpty()) args.put("time", time);
        if (count != null && count > 0) args.put("count", count);
        return callToolAndParse("get_kline", args);
    }

    public Map<String, Object> listFlash(String cursor) throws IOException {
        Map<String, Object> args = new LinkedHashMap<>();
        if (cursor != null && !cursor.isEmpty()) args.put("cursor", cursor);
        return callToolAndParse("list_flash", args);
    }

    public Map<String, Object> searchFlash(String keyword) throws IOException {
        return callToolAndParse("search_flash", Map.of("keyword", keyword));
    }

    public Map<String, Object> listNews(String cursor) throws IOException {
        Map<String, Object> args = new LinkedHashMap<>();
        if (cursor != null && !cursor.isEmpty()) args.put("cursor", cursor);
        return callToolAndParse("list_news", args);
    }

    public Map<String, Object> searchNews(String keyword, String cursor) throws IOException {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("keyword", keyword);
        if (cursor != null && !cursor.isEmpty()) args.put("cursor", cursor);
        return callToolAndParse("search_news", args);
    }

    public Map<String, Object> getNews(String id) throws IOException {
        return callToolAndParse("get_news", Map.of("id", id));
    }

    public Map<String, Object> listCalendar() throws IOException {
        return callToolAndParse("list_calendar", Map.of());
    }

    public List<String> getQuoteCodes() throws IOException {
        JsonNode resp = sendRequest("resources/read",
                objectMapper.createObjectNode().put("uri", "quote://codes"));
        JsonNode result = resp.get("result");
        if (result == null) return List.of();
        JsonNode contents = result.get("contents");
        if (contents == null || !contents.isArray()) return List.of();
        List<String> codes = new ArrayList<>();
        for (JsonNode content : contents) {
            String text = content.path("text").asText();
            if (!text.isEmpty()) {
                Arrays.stream(text.split("[,\\n]"))
                        .map(String::trim).filter(s -> !s.isEmpty()).forEach(codes::add);
            }
        }
        return codes;
    }

    // ═══════════════════ 核心私有方法 ═══════════════════

    private Map<String, Object> callToolAndParse(String toolName, Map<String, Object> arguments) throws IOException {
        ensureInitialized();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", objectMapper.valueToTree(arguments));

        JsonNode response = sendRequest("tools/call", params);
        JsonNode result = response.get("result");

        if (result == null) {
            String error = response.path("error").toString();
            throw new RuntimeException("工具 " + toolName + " 调用失败: " + error);
        }

        JsonNode structured = result.get("structuredContent");
        if (structured != null && !structured.isNull()) {
            return objectMapper.convertValue(structured, Map.class);
        }

        JsonNode contentArray = result.get("content");
        if (contentArray != null && contentArray.isArray()) {
            return Map.of("raw", objectMapper.convertValue(contentArray, List.class));
        }
        return objectMapper.convertValue(result, Map.class);
    }

    /**
     * 发送 JSON‑RPC 请求，自动处理 SSE 或 JSON 响应。
     */
    private JsonNode sendRequest(String method, ObjectNode params) throws IOException {
        int id = requestId.getAndIncrement();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null && !params.isEmpty()) {
            request.set("params", params);
        }

        HttpHeaders headers = buildHeaders();
        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(objectMapper.writeValueAsString(request), headers);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化请求失败", e);
        }

        String url = properties.getServerUrl();
        for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
            try {
                ResponseEntity<byte[]> re =
                        restTemplate.exchange(url, HttpMethod.POST, entity, byte[].class);
                MediaType contentType = re.getHeaders().getContentType();
                if (contentType != null && contentType.toString().contains("text/event-stream")) {
                    // 解析 SSE 事件流
                    return parseSseResponse(re.getBody());
                } else if (contentType != null && contentType.toString().contains("application/json")) {
                    // 直接解析 JSON 响应
                    return objectMapper.readTree(re.getBody());
                } else {
                    // 兜底：尝试 JSON 解析
                    return objectMapper.readTree(re.getBody());
                }
            } catch (HttpClientErrorException e) {
                throw new RuntimeException("MCP 请求错误: " + e.getMessage() + " body: " + e.getResponseBodyAsString(), e);
            } catch (RestClientException | JsonProcessingException e) {
                if (attempt == properties.getMaxRetries()) {
                    throw new RuntimeException("MCP 请求失败, 已重试 " + properties.getMaxRetries() + " 次", e);
                }
                log.warn("MCP 请求失败，重试 {}/{}: {}", attempt + 1, properties.getMaxRetries(), e.getMessage());
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
        throw new RuntimeException("MCP 请求失败 (不可能到达)");
    }

    /**
     * 解析 SSE 事件流，提取 JSON‑RPC 响应。
     */
    private JsonNode parseSseResponse(byte[] body) throws JsonProcessingException {
        StringBuilder dataBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    dataBuilder.append(data);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("SSE 解析失败", e);
        }

        String jsonStr = dataBuilder.toString().trim();
        if (jsonStr.isEmpty()) {
            throw new RuntimeException("SSE 响应中未找到 data 字段");
        }
        return objectMapper.readTree(jsonStr);
    }

    private void sendNotification(ObjectNode notification) {
        HttpHeaders headers = buildHeaders();
        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(objectMapper.writeValueAsString(notification), headers);
        } catch (JsonProcessingException e) {
            log.error("序列化通知失败", e);
            return;
        }
        try {
            restTemplate.postForEntity(properties.getServerUrl(), entity, String.class);
        } catch (Exception e) {
            log.error("发送 initialized 通知失败: {}", e.getMessage());
        }
    }

    /**
     * 构建标准请求头，关键增加 Accept 头以支持 SSE。
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        // 必须同时接受 JSON 与 SSE，否则服务端可能拒绝或返回异常流
        headers.set(HttpHeaders.ACCEPT, "application/json, text/event-stream");
        headers.set("MCP-Protocol-Version", properties.getProtocolVersion());
        return headers;
    }

    private void ensureInitialized() {
        if (!initialized.get()) {
            throw new RuntimeException("MCP 客户端尚未初始化");
        }
    }

    public boolean isConnected() { return initialized.get(); }
    public String getServerInfo() { return serverName + ":" + serverVersion; }
    public Set<String> getAvailableTools() { return Collections.unmodifiableSet(toolsCache.keySet()); }
}