package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.core.AgentRuntime;
import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.Response;
import com.thirdexploration.promengine.core.domain.UserInput;
import com.thirdexploration.promengine.web.model.ChatMessage;
import com.thirdexploration.promengine.web.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AgentRuntime agentRuntime;
    private final ChatMessageRepository chatMessageRepository;



    @PostMapping
    public CompletableFuture<Response> chat(@RequestBody ChatRequest request,
                                            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "default-user";
        String sessionId = request.sessionId();  // 由前端生成

        // 判断是否是新会话的第一条消息
        if (chatMessageRepository.isFirstMessage(uid, sessionId)) {
            String autoName = request.message();
            if (autoName.length() > 20) autoName = autoName.substring(0, 20) + "…";
            // 存储用户消息时带上自动生成的名称
            ChatMessage userMsg = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(uid)
                    .sessionId(sessionId)
                    .sessionName(autoName)
                    .role("user")
                    .content(request.message())
                    .timestamp(System.currentTimeMillis())
                    .createdAt(System.currentTimeMillis())
                    .build();
            chatMessageRepository.save(userMsg);
        } else {
            // 后续消息不带 sessionName（置空）
            ChatMessage userMsg = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(uid)
                    .sessionId(sessionId)
                    .sessionName(null)
                    .role("user")
                    .content(request.message())
                    .timestamp(System.currentTimeMillis())
                    .createdAt(System.currentTimeMillis())
                    .build();
            chatMessageRepository.save(userMsg);
        }
        UserInput input = UserInput.builder()
                .sessionId(request.sessionId())
                .text(request.message())
                .timestamp(System.currentTimeMillis())
                .userId(uid)
                .domain(null)            // 可选，从请求体获取
                .build();

        return agentRuntime.process(input).thenApply(response -> {
            // 2. 存储助手回复
            ChatMessage assistantMsg = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(uid)
                    .sessionId(request.sessionId())
                    .role("assistant")
                    .content(response.getText())
                    .timestamp(System.currentTimeMillis())
                    .createdAt(System.currentTimeMillis())
                    .build();
            chatMessageRepository.save(assistantMsg);
            return response;
        });

    }

    /**
     * 流式聊天接口（GET 方式，方便浏览器测试）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamGet(@RequestParam String sessionId, @RequestParam String message) {
        ChatRequest request = new ChatRequest(sessionId, message);
        return chatStream(request);
    }

    /**
     * 流式聊天接口（POST 方式，SSE 封装）
     */
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
                                    outputStream.write((delta.substring(4)).getBytes(StandardCharsets.UTF_8));
                                } else {
                                    outputStream.write((delta).getBytes(StandardCharsets.UTF_8));
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

    @PutMapping("/sessions/{sessionId}/name")
    public void renameSession(@PathVariable String sessionId,
                              @RequestBody Map<String, String> body,
                              @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "default-user";
        chatMessageRepository.updateSessionName(uid, sessionId, body.get("name"));
    }

    @GetMapping("/sessions")
    public List<SessionInfo> getSessions(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "default-user";
        List<ChatMessageRepository.SessionInfo> infos = chatMessageRepository.findDistinctSessions(uid);
        return infos.stream()
                .map(info -> new SessionInfo(info.id(), info.name()))
                .toList();
    }

//    /**
//     * 获取当前用户的所有会话列表（按最新消息时间降序）
//     */
//    @GetMapping("/sessions")
//    public List<SessionInfo> getSessions(@RequestHeader(value = "X-User-Id", required = false) String userId) {
//        String uid = userId != null ? userId : "default-user";
//        List<ChatMessageRepository.SessionInfo> sessionIds = chatMessageRepository.findDistinctSessions(uid);
//        return sessionIds.stream()
//                .map(id -> new SessionInfo(id.id(), "会话" + id.id().substring(0, 8)))
//                .toList();
//    }


    /**
     * 获取指定会话的所有消息（按时间升序）
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessage> getMessages(@PathVariable String sessionId,
                                         @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "default-user";
        return chatMessageRepository.findBySessionId(uid, sessionId);
    }

    // ---------- DTO ----------

    /** 聊天请求体 */
    public record ChatRequest(String sessionId, String message) {}

    /** 会话摘要信息（用于前端列表） */
    public record SessionInfo(String id, String name) {}
}