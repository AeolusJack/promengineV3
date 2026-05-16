package com.thirdexploration.promengine.executor.tool.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@ToolHandler(
        name = "http_request",
        description = "最强 HTTP 客户端，支持 GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS，" +
                "支持 JSON、表单、Multipart 文件上传，自动管理 Cookie，支持重定向、Basic/Bearer 认证、文件下载。",
        category = ToolHandler.Category.NETWORK,
        location = ToolHandler.Location.LOCAL,
        version = "2.0.0"
)
@SandboxPolicy(
        allowedPaths = {},
        maxExecutionSeconds = 60
)
@Component
public class HttpRequestHandler {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpRequestHandler() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(cookieManager)
                .build();
    }

    public String execute(
            @ToolParameter(value = "url", description = "请求 URL", example = "https://api.example.com/data")
            String url,
            @ToolParameter(value = "method", description = "HTTP 方法: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS", required = false, example = "GET")
            String method,
            @ToolParameter(value = "headers", description = "请求头，JSON 格式，如 {\"Authorization\":\"Bearer token\",\"User-Agent\":\"MyBot\"}", required = false)
            String headersJson,
            @ToolParameter(value = "body", description = "请求体（文本/JSON）", required = false)
            String body,
            @ToolParameter(value = "form_params", description = "表单参数，JSON 格式如 {\"key1\":\"val1\",\"key2\":\"val2\"}，自动以 application/x-www-form-urlencoded 发送", required = false)
            String formParamsJson,
            @ToolParameter(value = "multipart_files", description = "Multipart 文件上传，JSON 格式如 {\"fieldName\":\"/absolute/or/relative/path/to/file\"}", required = false)
            String multipartFilesJson,
            @ToolParameter(value = "basic_auth", description = "Basic 认证，格式 \"username:password\"", required = false)
            String basicAuth,
            @ToolParameter(value = "bearer_token", description = "Bearer Token", required = false)
            String bearerToken,
            @ToolParameter(value = "timeout_seconds", description = "请求超时秒数，默认30", required = false, example = "30")
            Integer timeoutSeconds,
            @ToolParameter(value = "follow_redirects", description = "是否自动跟随重定向，默认true", required = false, example = "true")
            Boolean followRedirects,
            @ToolParameter(value = "max_response_chars", description = "响应内容最大返回字符数，超出则截断。默认5000，设为-1则不截断", required = false, example = "5000")
            Integer maxResponseChars,
            @ToolParameter(value = "save_to_file", description = "将响应内容保存到本地文件路径（绝对路径或相对用户目录），用于大文件", required = false)
            String saveToFile
    ) throws Exception {
        // 参数默认值
        String httpMethod = (method == null || method.isBlank()) ? "GET" : method.toUpperCase();
        int timeout = (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 30;
        boolean follow = (followRedirects == null) || followRedirects;
        int maxChars = (maxResponseChars != null) ? maxResponseChars : 5000;

        // 构建请求器
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeout));

        // 按需创建 HttpClient（用于控制是否跟随重定向）
        HttpClient clientForRequest = this.httpClient;
        if (!follow) {
            clientForRequest = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeout))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        }

        // 认证头
        if (basicAuth != null && !basicAuth.isBlank()) {
            String encoded = Base64.getEncoder().encodeToString(basicAuth.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        }
        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        // 自定义请求头
        if (headersJson != null && !headersJson.isBlank()) {
            Map<String, String> headers = parseJsonMap(headersJson);
            headers.forEach(builder::header);
        }

        // 默认 User-Agent
        if (!builder.build().headers().firstValue("User-Agent").isPresent()) {
            builder.header("User-Agent", "Mozilla/5.0 (compatible; PromEngineBot/2.0)");
        }

        // 处理请求体
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
        if (formParamsJson != null && !formParamsJson.isBlank()) {
            Map<String, String> params = parseJsonMap(formParamsJson);
            String formBody = params.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                              URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            bodyPublisher = HttpRequest.BodyPublishers.ofString(formBody);
            builder.header("Content-Type", "application/x-www-form-urlencoded");
            httpMethod = "POST";
        } else if (multipartFilesJson != null && !multipartFilesJson.isBlank()) {
            Map<String, String> fileFields = parseJsonMap(multipartFilesJson);
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            builder.header("Content-Type", "multipart/form-data; boundary=" + boundary);
            bodyPublisher = buildMultipartBody(fileFields, boundary);
            httpMethod = "POST";
        } else if (body != null && !body.isBlank()) {
            bodyPublisher = HttpRequest.BodyPublishers.ofString(body);
            if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
                builder.header("Content-Type", "application/json");
            } else {
                builder.header("Content-Type", "text/plain;charset=UTF-8");
            }
        }

        builder.method(httpMethod, bodyPublisher);

        // 发送请求
        HttpResponse<byte[]> response = clientForRequest.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        int statusCode = response.statusCode();
        byte[] responseBytes = response.body();
        String responseStr = new String(responseBytes, StandardCharsets.UTF_8);

        // 保存到文件
        if (saveToFile != null && !saveToFile.isBlank()) {
            Path savePath = Paths.get(saveToFile);
            if (!savePath.isAbsolute()) {
                savePath = Paths.get(System.getProperty("user.dir")).resolve(savePath);
            }
            Files.createDirectories(savePath.getParent());
            Files.write(savePath, responseBytes);
            return String.format("HTTP %d\n响应已保存至文件: %s\n文件大小: %d 字节\n响应头:\n%s",
                    statusCode,
                    savePath.toAbsolutePath(),
                    responseBytes.length,
                    formatHeaders(response.headers()));  // 修正：传入 HttpHeaders
        }

        // 内容截断
        if (responseStr.length() > maxChars && maxChars > 0) {
            responseStr = responseStr.substring(0, maxChars) + "\n... (响应体过长，已截断)";
        } else if (maxChars == -1) {
            // 不截断
        }

        return String.format("HTTP %d\n%s\n\n响应头:\n%s",
                statusCode,
                responseStr,
                formatHeaders(response.headers()));  // 修正：传入 HttpHeaders
    }

    private Map<String, String> parseJsonMap(String json) throws IOException {
        if (json == null || json.isBlank()) return Map.of();
        return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
    }

    private HttpRequest.BodyPublisher buildMultipartBody(Map<String, String> fileFields, String boundary) throws IOException {
        List<byte[]> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : fileFields.entrySet()) {
            String fieldName = entry.getKey();
            String filePathStr = entry.getValue();
            Path filePath = Paths.get(filePathStr);
            if (!filePath.isAbsolute()) {
                filePath = Paths.get(System.getProperty("user.dir")).resolve(filePathStr);
            }
            if (!Files.exists(filePath)) {
                throw new IllegalArgumentException("上传文件不存在: " + filePath.toAbsolutePath());
            }
            byte[] fileBytes = Files.readAllBytes(filePath);
            String fileName = filePath.getFileName().toString();
            String partHeader = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n";
            parts.add(partHeader.getBytes(StandardCharsets.UTF_8));
            parts.add(fileBytes);
            parts.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }

    // 修正后的方法：接收 HttpHeaders 对象
    private String formatHeaders(HttpHeaders headers) {
        return headers.map().entrySet().stream()
                .map(e -> e.getKey() + ": " + String.join(", ", e.getValue()))
                .collect(Collectors.joining("\n"));
    }
}