package com.thirdexploration.promengine.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.AgentRuntime;
import com.thirdexploration.promengine.core.cache.StreamFragmentStore;
import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.Response;
import com.thirdexploration.promengine.core.domain.UserInput;
import com.thirdexploration.promengine.runtime.model.ChatMessage;
import com.thirdexploration.promengine.runtime.model.AgentRecord;
import com.thirdexploration.promengine.runtime.repository.ChatMessageRepository;
import com.thirdexploration.promengine.runtime.service.AgentService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final AgentRuntime agentRuntime;
    private final ChatMessageRepository chatMessageRepository;
    private final AgentService agentService;
    private final StreamFragmentStore streamFragmentStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("stream-worker-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    public void shutdown() {
        streamExecutor.shutdown();
        asyncExecutor.shutdown();
        try {
            if (!streamExecutor.awaitTermination(10, TimeUnit.SECONDS)) streamExecutor.shutdownNow();
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) asyncExecutor.shutdownNow();
        } catch (InterruptedException e) {
            streamExecutor.shutdownNow();
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @PostMapping
    public CompletableFuture<Response> chat(@RequestBody ChatRequest request,
                                            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = (userId != null && !userId.isBlank()) ? userId : "default-user";
        String sessionId = request.sessionId();
        String executionId = UUID.randomUUID().toString();

        boolean isFirst = chatMessageRepository.isFirstMessage(uid, sessionId);
        saveUserMessage(uid, sessionId, executionId, request.message(), isFirst);

        UserInput input = buildUserInput(request, uid, executionId);

        return CompletableFuture.supplyAsync(() -> agentRuntime.process(input).join(), asyncExecutor)
                .thenApply(response -> {
                    saveAssistantMessage(uid, sessionId, executionId, response.getText());
                    return response;
                })
                .exceptionally(ex -> {
                    log.error("Chat processing failed", ex);
                    String errorMsg = "处理失败: " + ex.getMessage();
                    saveAssistantMessage(uid, sessionId, executionId, errorMsg);
                    return Response.builder().text(errorMsg).processingTimeMs(0).modelUsed("error").cost(0.0).build();
                });
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request,
                                 @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = (userId != null && !userId.isBlank()) ? userId : "default-user";
        String executionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(1260_000L);

        saveUserMessage(uid, request.sessionId(), executionId, request.message(), false);
        UserInput input = buildUserInput(request, uid, executionId);

        AtomicBoolean completed = new AtomicBoolean(false);
        ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (!completed.get()) {
                try { emitter.send(SseEmitter.event().comment("heartbeat")); } catch (IOException ignored) {}
            }
        }, 30, 30, TimeUnit.SECONDS);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                executeStream(request, uid, executionId, input, emitter, completed);
            } catch (Exception e) {
                log.error("Fatal error in stream processing", e);
                if (!completed.get()) {
                    safeSendEvent(emitter, "[DONE]", completed);
                    safeCompleteWithError(emitter, e, completed);
                }
            } finally {
                heartbeatExecutor.shutdown();
            }
        }, streamExecutor);

       // 兜底保障：无论 executeStream 是否正常结束，都尝试发送 DONE 并完成 emitter
        future.whenComplete((v, ex) -> {
            if (!completed.get()) {
                log.warn("Stream completed but DONE not sent, sending now for session: {}", request.sessionId());
                safeSendEvent(emitter, "[DONE]", completed);
                safeComplete(emitter, completed);
            }
        });

        // 修复超时处理：不发送消息，直接完成
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for session: {}", request.sessionId());
            if (!completed.get()) {
                safeSendEvent(emitter, "[DONE]", completed);
                safeComplete(emitter, completed);
            }
            heartbeatExecutor.shutdown();
        });
        emitter.onCompletion(() -> {
            completed.set(true);
            heartbeatExecutor.shutdown();
        });
        emitter.onError(ex -> {
            log.warn("SSE error for session: {}", request.sessionId(), ex);
            if (!completed.get()) safeCompleteWithError(emitter, ex, completed);
            heartbeatExecutor.shutdown();
        });

        return emitter;
    }

    private void executeStream(ChatRequest request, String userId, String executionId,
                               UserInput input, SseEmitter emitter, AtomicBoolean completed) {
        StringBuilder fullContent = new StringBuilder();
        boolean[] dataReceived = {false};

        try (Stream<CompletionChunk> chunkStream = agentRuntime.processStream(input)) {
            chunkStream.forEach(chunk -> {
                dataReceived[0] = true;
                if (completed.get()) return;
                try {
                    // 在 chunk 处理循环内：
                    if (chunk.isLast()) {
                        // 发送最终的 DONE 事件
                        safeSendEvent(emitter, "[DONE]", completed);
                        safeComplete(emitter, completed);
                        String finalContent = fullContent.toString();
                        if (finalContent.isBlank()) finalContent = "处理完成，无文本返回。";
                        saveAssistantMessage(userId, request.sessionId(), executionId, finalContent);
                        streamFragmentStore.markCompleted(executionId);
                    } else {
                        // 正常内容推送
                        String delta = chunk.getDelta();
                        if (delta != null && !delta.isEmpty()) {
                            fullContent.append(delta);
                            safeSendEvent(emitter, delta, completed);
                            streamFragmentStore.addFragment(executionId, delta);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing chunk", e);
                    safeCompleteWithError(emitter, e, completed);
                    throw new RuntimeException(e);
                }
            });

            if (!dataReceived[0] && !completed.get()) {
                log.warn("Stream returned no data for session: {}", request.sessionId());
                String errorMsg = "后台处理未返回任何内容，请稍后重试。";
                safeSendEvent(emitter, errorMsg, completed);
                safeSendEvent(emitter, "[DONE]", completed);
                safeComplete(emitter, completed);
                saveAssistantMessage(userId, request.sessionId(), executionId, errorMsg);
                streamFragmentStore.markCompleted(executionId);
            }
        } catch (Exception e) {
            log.error("Stream processing error", e);
            if (!completed.get()) {
                String errorMsg = "流式处理错误: " + e.getMessage();
                safeSendEvent(emitter, errorMsg, completed);
                safeSendEvent(emitter, "[DONE]", completed);
                safeCompleteWithError(emitter, e, completed);
                saveAssistantMessage(userId, request.sessionId(), executionId, errorMsg);
                streamFragmentStore.markCompleted(executionId);
            }
        }
    }

    // 新增：获取流式片段（供前端刷新后恢复）
    @GetMapping("/stream-fragments/{executionId}")
    public Map<String, Object> getStreamFragments(@PathVariable String executionId) {
        List<String> fragments = streamFragmentStore.getFragments(executionId);
        String assembled = streamFragmentStore.getAssembledContent(executionId);
        boolean completed = streamFragmentStore.isCompleted(executionId);
        return Map.of(
                "executionId", executionId,
                "fragments", fragments,
                "assembled", assembled,
                "completed", completed
        );
    }

    // ---------- 辅助方法 ----------
    private void safeSendEvent(SseEmitter emitter, String data, AtomicBoolean completed) {
        if (completed.get()) return;
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException e) {
            log.debug("Failed to send SSE data: {}", e.getMessage());
            completed.set(true);
        }
    }

    private void safeComplete(SseEmitter emitter, AtomicBoolean completed) {
        if (completed.get()) return;
        completed.set(true);
        try {
            emitter.complete();
        } catch (Exception ignored) {}
    }

    private void safeCompleteWithError(SseEmitter emitter, Throwable ex, AtomicBoolean completed) {
        if (completed.get()) return;
        completed.set(true);
        try { emitter.completeWithError(ex); } catch (Exception ignored) {}
    }


    // ---------- 辅助方法 ----------
    private void saveUserMessage(String userId, String sessionId, String executionId, String content, boolean isFirst) {
        ChatMessage.ChatMessageBuilder builder = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .sessionId(sessionId)
                .executionId(executionId)
                .role("user")
                .content(content)
                .timestamp(System.currentTimeMillis())
                .createdAt(System.currentTimeMillis());
        if (isFirst) {
            String autoName = content.length() > 20 ? content.substring(0, 20) + "…" : content;
            builder.sessionName(autoName);
        }
        chatMessageRepository.save(builder.build());
    }

    private void saveAssistantMessage(String userId, String sessionId, String executionId, String content) {
        ChatMessage assistantMsg = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .sessionId(sessionId)
                .executionId(executionId)
                .role("assistant")
                .content(content)
                .timestamp(System.currentTimeMillis())
                .createdAt(System.currentTimeMillis())
                .build();
        chatMessageRepository.save(assistantMsg);
    }

    private UserInput buildUserInput(ChatRequest request, String userId, String executionId) {
        UserInput.UserInputBuilder builder = UserInput.builder()
                .sessionId(request.sessionId())
                .text(request.message())
                .userId(userId)
                .metadata(new HashMap<>(Map.of("executionId", executionId)))
                .timestamp(System.currentTimeMillis());

        if (request.agentId() != null && !request.agentId().isBlank()) {
            try {
                AgentRecord agent = agentService.getAgentRecord(request.agentId());
                if (agent != null) {
                    Map<String, Object> agentConfig = new HashMap<>();
                    agentConfig.put("systemPrompt", agent.getSystemPrompt());
                    agentConfig.put("tools", parseTools(agent.getTools()));
                    agentConfig.put("skills", parseSkills(agent.getSkills()));
                    agentConfig.put("modelPreference", agent.getModelPreference());
                    agentConfig.put("memoryDomain", agent.getMemoryDomain());
                    agentConfig.put("agentId", agent.getId());
                    builder.metadata(Map.of("agentConfig", agentConfig));
                }
            } catch (Exception e) {
                log.warn("Failed to load agent config for agentId={}", request.agentId(), e);
            }
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private List<String> parseTools(String toolsJson) {
        if (toolsJson == null || toolsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(toolsJson, List.class);
        } catch (Exception e) {
            log.debug("Failed to parse tools JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseSkills(String skillsJson) {
        return parseTools(skillsJson);
    }

    // ---------- 其他端点 ----------
    @PostMapping(value = "/stream-debug", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody chatStreamDebug(@RequestBody ChatRequest request) {
        UserInput input = UserInput.builder()
                .sessionId(request.sessionId())
                .text(request.message())
                .timestamp(System.currentTimeMillis())
                .build();
        return outputStream -> {
            try (Stream<CompletionChunk> chunkStream = agentRuntime.processStream(input)) {
                chunkStream.forEach(chunk -> {
                    try {
                        if (!chunk.isLast()) {
                            outputStream.write(chunk.getDelta().getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();
                        } else {
                            outputStream.write("[DONE]".getBytes(StandardCharsets.UTF_8));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                try {
                    outputStream.write(("Error: " + e.getMessage()).getBytes());
                } catch (IOException ignored) {}
            }
        };
    }

    @PutMapping("/sessions/{sessionId}/name")
    public void renameSession(@PathVariable String sessionId, @RequestBody Map<String, String> body,
                              @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = (userId != null && !userId.isBlank()) ? userId : "default-user";
        chatMessageRepository.updateSessionName(uid, sessionId, body.get("name"));
    }

    @GetMapping("/sessions")
    public List<SessionInfo> getSessions(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = (userId != null && !userId.isBlank()) ? userId : "default-user";
        return chatMessageRepository.findDistinctSessions(uid).stream()
                .map(info -> new SessionInfo(info.id(), info.name()))
                .toList();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessage> getMessages(@PathVariable String sessionId,
                                         @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = (userId != null && !userId.isBlank()) ? userId : "default-user";
        return chatMessageRepository.findBySessionId(uid, sessionId);
    }

    public record ChatRequest(String sessionId, String message, String agentId) {}
    public record SessionInfo(String id, String name) {}
}