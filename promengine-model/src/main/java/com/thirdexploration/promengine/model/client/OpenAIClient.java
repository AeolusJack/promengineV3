package com.thirdexploration.promengine.model.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.CompletionRequest;
import com.thirdexploration.promengine.core.domain.CompletionResult;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Component
public class OpenAIClient {

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletionResult complete(String endpoint, String apiKey, CompletionRequest request) {
        String url = endpoint + "/v1/chat/completions";
        Map<String, Object> body = Map.of(
                "model", request.getModelId(),
                "messages", new Object[]{Map.of("role", "user", "content", request.getPrompt())},
                "max_tokens", request.getMaxTokens(),
                "temperature", request.getTemperature(),
                "stream", false
        );
        try {
            RequestBody requestBody = RequestBody.create(
                    objectMapper.writeValueAsString(body),
                    MediaType.parse("application/json"));
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(requestBody)
                    .build();
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("API error: " + response.code() + " " + response.body().string());
                }
                JsonNode node = objectMapper.readTree(response.body().string());
                JsonNode choice = node.get("choices").get(0);
                String content = choice.get("message").get("content").asText();
                JsonNode usage = node.get("usage");
                long promptTokens = usage.get("prompt_tokens").asLong();
                long completionTokens = usage.get("completion_tokens").asLong();
                return CompletionResult.builder()
                        .content(content)
                        .finishReason(choice.get("finish_reason").asText())
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .latencyMs(0) // 可从响应头获取
                        .build();
            }
        } catch (IOException e) {
            throw new RuntimeException("OpenAI request failed", e);
        }
    }

    public Stream<CompletionChunk> stream(String endpoint, String apiKey, CompletionRequest request) {
        // 返回一个 Stream 实现流式处理，较复杂，暂略
        return Stream.empty();
    }
}