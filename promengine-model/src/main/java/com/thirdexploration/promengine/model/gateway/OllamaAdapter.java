package com.thirdexploration.promengine.model.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.CompletionRequest;
import com.thirdexploration.promengine.core.domain.CompletionResult;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Component
public class OllamaAdapter implements ModelAdapter {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${promengine.models.providers[?(@.id=='ollama')].endpoint:http://localhost:11434}")
    private String endpoint;

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 500;
    private static final Duration STREAM_READ_TIMEOUT = Duration.ofSeconds(120);

    public OllamaAdapter() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(STREAM_READ_TIMEOUT)
                .writeTimeout(Duration.ofSeconds(30))
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build();
    }

    @Override
    public String getProviderId() {
        return "ollama";
    }

    // ------------------- 非流式请求 -------------------
    @Override
    public CompletionResult complete(CompletionRequest request) {
        long startTime = System.currentTimeMillis();
        String modelId = request.getModelId();
        boolean includeThinking = request.isIncludeThinking();
        log.info("Ollama request started, model: {}, includeThinking: {}", modelId, includeThinking);

        Map<String, Object> body = buildRequestBody(request, false, includeThinking);

        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String jsonBody = objectMapper.writeValueAsString(body);
                log.debug("Ollama request body (attempt {}): {}", attempt + 1, jsonBody);

                Request httpRequest = new Request.Builder()
                        .url(endpoint + "/api/generate")
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                        .build();

                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        log.error("Ollama error response: {} - {}", response.code(), errorBody);
                        throw new IOException("Ollama error: " + response.code());
                    }

                    String responseBody = response.body().string();
                    JsonNode node = objectMapper.readTree(responseBody);

                    // 组装最终内容：如果启用思考且存在 thinking 字段，则拼接
                    StringBuilder fullContent = new StringBuilder();
                    if (includeThinking && node.has("thinking")) {
                        String thinking = node.path("thinking").asText();
                        if (!thinking.isEmpty()) {
                            fullContent.append("[思考] ").append(thinking).append("\n\n");
                        }
                    }
                    fullContent.append(node.path("response").asText(""));

                    long evalCount = node.path("eval_count").asLong(0);
                    long promptEvalCount = node.path("prompt_eval_count").asLong(0);
                    long totalDuration = node.path("total_duration").asLong(0) / 1_000_000;

                    log.debug("Ollama response: tokens(prompt={}, completion={}), duration={}ms",
                            promptEvalCount, evalCount, totalDuration);

                    return CompletionResult.builder()
                            .content(fullContent.toString())
                            .finishReason("stop")
                            .promptTokens(promptEvalCount)
                            .completionTokens(evalCount)
                            .latencyMs(System.currentTimeMillis() - startTime)
                            .build();
                }
            } catch (IOException e) {
                lastException = e;
                log.warn("Ollama request failed (attempt {}/{}): {}", attempt + 1, MAX_RETRIES + 1, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        log.error("Ollama request failed after {} attempts", MAX_RETRIES + 1, lastException);
        throw new RuntimeException("Ollama request failed after retries", lastException);
    }

    // ------------------- 流式请求 -------------------
    @Override
    public Stream<CompletionChunk> stream(CompletionRequest request) {
        String modelId = request.getModelId();
        boolean includeThinking = request.isIncludeThinking();
        log.info("Ollama streaming started, model: {}, includeThinking: {}, prompt length: {}",
                modelId, includeThinking, request.getPrompt().length());

        Map<String, Object> body = buildRequestBody(request, true, includeThinking);

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            log.info("最终请求的json：{}",jsonBody);
            Request httpRequest = new Request.Builder()
                    .url(endpoint + "/api/generate")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();

            Response response = httpClient.newCall(httpRequest).execute();
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                log.error("Ollama stream error: {} - {}", response.code(), errorBody);
                response.close();
                throw new IOException("Ollama stream error: " + response.code());
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                response.close();
                return Stream.empty();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()));
            Stream<String> lineStream = reader.lines()
                    .onClose(() -> {
                        try {
                            reader.close();
                            response.close();
                            log.debug("Ollama stream resources closed");
                        } catch (IOException e) {
                            log.warn("Error closing Ollama stream", e);
                        }
                    });

            return lineStream
//                    .peek(line -> log.info("Ollama raw line: {}", line))
                    .map(line -> parseChunk(line, includeThinking))
                    .filter(Objects::nonNull)
                    .takeWhile(chunk -> !Thread.currentThread().isInterrupted());

        } catch (IOException e) {
            log.error("Ollama stream request failed", e);
            throw new RuntimeException("Ollama stream failed", e);
        }
    }

    // ------------------- 解析单个流式块 -------------------
    private CompletionChunk parseChunk(String line, boolean includeThinking) {
        try {
            if (line == null || line.isBlank()) return null;
            JsonNode node = objectMapper.readTree(line);
            boolean done = node.path("done").asBoolean(false);
            // 1. 如果启用思考且存在 thinking 字段（无论是否为空字符串）
            if (includeThinking && node.has("thinking")) {
                String thinking = node.path("thinking").asText();
                // 即使为空也返回一个块，让前端知道思考正在进行（可选）
                // 如果希望过滤真正的空内容，可以保留 !thinking.isEmpty() 判断
                return CompletionChunk.builder()
                        .delta("[思考] " + thinking)   // 即使 thinking 为空，也发送一个标记
                        .last(false)
                        .build();
            }
            // 2. 正式回复内容
            String response = node.path("response").asText("");
            if (!response.isEmpty()) {
                return CompletionChunk.builder()
                        .delta(response)
                        .last(false)
                        .build();
            }

            // 3. 结束标记
            if (done) {
                return CompletionChunk.builder().delta("").last(true).build();
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to parse stream line: {}", line, e);
            return null;
        }
    }

    // ------------------- 健康检查 -------------------
    @Override
    public boolean isAvailable() {
        try {
            Request request = new Request.Builder()
                    .url(endpoint + "/api/tags")
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            log.warn("Ollama health check failed: {}", e.getMessage());
            return false;
        }
    }

    // ------------------- 构建请求体（支持 think 参数） -------------------
    private Map<String, Object> buildRequestBody(CompletionRequest request, boolean stream, boolean includeThinking) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModelId());
        body.put("prompt", request.getPrompt());
        body.put("stream", stream);
        Map<String, Object> options = new HashMap<>();
        // 根据 includeThinking 决定是否添加 "think" 参数（Gemma 特有）
        if (includeThinking) {
//            body.put("think", true);
            options.put("think",true);
        }

        options.put("temperature", request.getTemperature());
        options.put("num_predict", request.getMaxTokens() > 0 ? request.getMaxTokens() : 512);
        body.put("options", options);

        return body;
    }
}