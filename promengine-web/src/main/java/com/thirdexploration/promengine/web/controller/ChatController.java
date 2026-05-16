package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.core.AgentRuntime;
import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.Response;
import com.thirdexploration.promengine.core.domain.UserInput;
import com.thirdexploration.promengine.runtime.model.ChatMessage;
import com.thirdexploration.promengine.runtime.model.AgentRecord;
import com.thirdexploration.promengine.runtime.repository.ChatMessageRepository;
import com.thirdexploration.promengine.runtime.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
    private final AgentService agentService;   // 新增依赖

    @PostMapping
    public CompletableFuture<Response> chat(@RequestBody ChatRequest request,
                                            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "default-user";
        String sessionId = request.sessionId();
        String executionId = UUID.randomUUID().toString();
        // 保存用户消息（与之前一致）
        if (chatMessageRepository.isFirstMessage(uid, sessionId)) {
            String autoName = request.message();
            if (autoName.length() > 20) autoName = autoName.substring(0, 20) + "…";
            ChatMessage userMsg = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(uid)
                    .sessionId(sessionId)
                    .executionId(executionId)
                    .sessionName(autoName)
                    .role("user")
                    .content(request.message())
                    .timestamp(System.currentTimeMillis())
                    .createdAt(System.currentTimeMillis())
                    .build();
            chatMessageRepository.save(userMsg);
        } else {
            ChatMessage userMsg = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(uid)
                    .sessionId(sessionId)
                    .executionId(executionId)
                    .role("user")
                    .content(request.message())
                    .timestamp(System.currentTimeMillis())
                    .createdAt(System.currentTimeMillis())
                    .build();
            chatMessageRepository.save(userMsg);
        }

        // 构建 UserInput，如果有 agentId 则加载配置
        UserInput.UserInputBuilder inputBuilder = UserInput.builder()
                .sessionId(sessionId)
                .text(request.message())
                .timestamp(System.currentTimeMillis())
                .userId(uid)
                .metadata(Map.of( "executionId", executionId))
                .domain(null);

        // 处理 Agent 上下文
        if (request.agentId() != null && !request.agentId().isBlank()) {
            try {
                AgentRecord agent = agentService.getAgentRecord(request.agentId());
                if (agent != null) {
                    Map<String, Object> agentConfig = new HashMap<>();
                    agentConfig.put("systemPrompt", agent.getSystemPrompt());
                    agentConfig.put("tools", parseTools(agent.getTools()));        // List<String>
                    agentConfig.put("skills", parseSkills(agent.getSkills()));    // 可忽略
                    agentConfig.put("modelPreference", agent.getModelPreference());
                    agentConfig.put("memoryDomain", agent.getMemoryDomain());
                    agentConfig.put("agentId", agent.getId());
                    // 将配置放入 metadata
                    inputBuilder.metadata(Map.of("agentConfig", agentConfig));
                }
            } catch (Exception e) {
                log.warn("Failed to load agent config for agentId={}: {}", request.agentId(), e.getMessage());
            }
        }

        UserInput input = inputBuilder.build();
        return agentRuntime.process(input).thenApply(response -> {
            // 存储助手消息
            ChatMessage assistantMsg = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(uid)
                    .sessionId(sessionId)
                    .executionId(executionId)
                    .role("assistant")
                    .content(response.getText())
                    .timestamp(System.currentTimeMillis())
                    .createdAt(System.currentTimeMillis())
                    .build();
            chatMessageRepository.save(assistantMsg);
            return response;
        });
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request,
                                 @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "default-user";
        SseEmitter emitter = new SseEmitter(120_000L);
        String executionId = UUID.randomUUID().toString();
        // 保存用户消息
        ChatMessage userMsg = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .userId(uid)
                .sessionId(request.sessionId())
                .executionId(executionId)
                .role("user")
                .content(request.message())
                .timestamp(System.currentTimeMillis())
                .createdAt(System.currentTimeMillis())
                .build();
        chatMessageRepository.save(userMsg);

        // 构建输入，同样处理 agentId
        UserInput.UserInputBuilder inputBuilder = UserInput.builder()
                .sessionId(request.sessionId())
                .text(request.message())
                .userId(uid)
                .metadata(Map.of("executionId", executionId))
                .timestamp(System.currentTimeMillis());
        if (request.agentId() != null && !request.agentId().isBlank()) {
            try {
                AgentRecord agent = agentService.getAgentRecord(request.agentId());
                if (agent != null) {
                    Map<String, Object> agentConfig = new HashMap<>();
                    agentConfig.put("systemPrompt", agent.getSystemPrompt());
                    agentConfig.put("tools", parseTools(agent.getTools()));
                    agentConfig.put("modelPreference", agent.getModelPreference());
                    agentConfig.put("memoryDomain", agent.getMemoryDomain());
                    agentConfig.put("agentId", agent.getId());
                    inputBuilder.metadata(Map.of("agentConfig", agentConfig));
                }
            } catch (Exception e) {
                log.warn("Failed to load agent config for stream", e);
            }
        }

        UserInput input = inputBuilder.build();
        StringBuilder fullContent = new StringBuilder();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Stream<CompletionChunk> chunkStream = agentRuntime.processStream(input);
                chunkStream.forEach(chunk -> {
                    try {
                        if (chunk.isLast()) {
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                            ChatMessage assistantMsg = ChatMessage.builder()
                                    .id(UUID.randomUUID().toString())
                                    .userId(uid)
                                    .sessionId(request.sessionId())
                                    .executionId(executionId)
                                    .role("assistant")
                                    .content(fullContent.toString())
                                    .timestamp(System.currentTimeMillis())
                                    .createdAt(System.currentTimeMillis())
                                    .build();
                            chatMessageRepository.save(assistantMsg);
                        } else {
                            String delta = chunk.getDelta();
                            fullContent.append(delta);
                            emitter.send(SseEmitter.event().data(delta));
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
        return emitter;
    }

    // 辅助方法：解析 tools JSON 数组字符串为 List<String>
    @SuppressWarnings("unchecked")
    private List<String> parseTools(String toolsJson) {
        if (toolsJson == null || toolsJson.isBlank()) return List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(toolsJson, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseSkills(String skillsJson) {
        return parseTools(skillsJson); // 同类解析
    }

    // 其他方法保持不变...
    @PostMapping(value = "/stream-debug", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody chatStreamDebug(@RequestBody ChatRequest request) {
        // 简化：不处理 agentId，可直接复用原逻辑
        UserInput input = UserInput.builder()
                .sessionId(request.sessionId())
                .text(request.message())
                .timestamp(System.currentTimeMillis())
                .build();
        return outputStream -> {
            Stream<CompletionChunk> chunkStream = agentRuntime.processStream(input);
            try (Stream<CompletionChunk> stream = chunkStream) {
                stream.forEach(chunk -> {
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
                try { outputStream.write(("Error: " + e.getMessage()).getBytes()); } catch (IOException ignored) {}
            }
        };
    }

    @PutMapping("/sessions/{sessionId}/name")
    public void renameSession(@PathVariable String sessionId, @RequestBody Map<String, String> body,
                              @RequestHeader(value = "X-User-Id", required = false) String userId) {
        chatMessageRepository.updateSessionName(userId != null ? userId : "default-user", sessionId, body.get("name"));
    }

    @GetMapping("/sessions")
    public List<SessionInfo> getSessions(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "default-user";
        return chatMessageRepository.findDistinctSessions(uid).stream()
                .map(info -> new SessionInfo(info.id(), info.name())).toList();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessage> getMessages(@PathVariable String sessionId,
                                         @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return chatMessageRepository.findBySessionId(userId != null ? userId : "default-user", sessionId);
    }

    public record ChatRequest(String sessionId, String message, String agentId) {}
    public record SessionInfo(String id, String name) {}
}