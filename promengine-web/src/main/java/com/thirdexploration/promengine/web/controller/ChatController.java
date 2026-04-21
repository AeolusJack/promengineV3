package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.core.AgentRuntime;
import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.Response;
import com.thirdexploration.promengine.core.domain.UserInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;


@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AgentRuntime agentRuntime;

    @PostMapping
    public CompletableFuture<Response> chat(@RequestBody ChatRequest request, @RequestHeader(value = "X-User-Id",required = false) String userId) {
        UserInput input = UserInput.builder()
                .sessionId(request.sessionId())    // ✅ 修正：record 使用字段名作为访问器
                .text(request.message())           // ✅ 修正：record 使用字段名作为访问器
                .timestamp(System.currentTimeMillis())
                .userId(userId)                         // 从请求头获取
                .domain(null)            // 可选，从请求体获取
                .build();
        return agentRuntime.process(input);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamGet(@RequestParam String sessionId, @RequestParam String message) {
        ChatRequest request = new ChatRequest(sessionId, message);
        return chatStream(request); // 复用 POST 逻辑
    }


    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L); // 120秒超时

        UserInput input = UserInput.builder()
                .sessionId(request.sessionId())
                .text(request.message())
                .timestamp(System.currentTimeMillis())
                .build();

        // 在独立线程中处理，避免阻塞主线程
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Stream<CompletionChunk> chunkStream = agentRuntime.processStream(input);
                chunkStream.forEach(chunk -> {
                    try {
                        if (chunk.isLast()) {
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                        } else {
                            emitter.send(SseEmitter.event().data(chunk.getDelta()));
                        }
                    } catch (IOException e) {
                        log.error("SSE send error", e);
                        emitter.completeWithError(e);
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                log.error("Stream processing error", e);
                emitter.completeWithError(e);
            }
        });

        // 设置超时和错误回调
        emitter.onTimeout(() -> log.warn("SSE emitter timed out"));
        emitter.onError(e -> log.error("SSE emitter error", e));
        emitter.onCompletion(() -> log.debug("SSE emitter completed"));

        return emitter;
    }

    /**
     * 纯文本流式接口，用于调试，直接输出模型返回的字符流，无 SSE 封装。
     */
    @PostMapping(value = "/stream-debug", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody chatStreamDebug(@RequestBody ChatRequest request) {
        UserInput input = UserInput.builder()
                .sessionId(request.sessionId())
                .text(request.message())
                .timestamp(System.currentTimeMillis())
                .build();

        return outputStream -> {
            Stream<CompletionChunk> chunkStream = null;
            try {
                chunkStream = agentRuntime.processStream(input);
                // 确保流在使用后关闭
                try (Stream<CompletionChunk> stream = chunkStream) {
                    stream.forEach(chunk -> {
                        try {
                            if (!chunk.isLast()) {
                                String delta = chunk.getDelta();
                                // 调试模式：根据内容类型添加前缀，便于区分思考和回复
                                if (delta.startsWith("[思考] ")) {
                                        outputStream.write((  delta.substring(4)  ).getBytes(StandardCharsets.UTF_8));
                                } else {
                                    outputStream.write(( delta).getBytes(StandardCharsets.UTF_8));
                                }
                                outputStream.flush(); // 立即刷新，实现逐字输出效果
                            } else {
                                outputStream.write("[DONE]".getBytes(StandardCharsets.UTF_8));
                            }
                        } catch (IOException e) {
                            log.error("Error writing stream debug response", e);
                            throw new RuntimeException(e);
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Stream debug processing error", e);
                try {
                    outputStream.write(("Error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) {}
            }
        };
    }


    public record ChatRequest(String sessionId, String message) {



    }
}