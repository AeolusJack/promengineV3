package com.thirdexploration.promengine.executor;

import com.nimbusds.jose.shaded.json.JSONObject;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jose.util.JSONStringUtils;
import com.thirdexploration.promengine.core.domain.*;
import com.thirdexploration.promengine.executor.config.OrchestratorProperties;
import com.thirdexploration.promengine.executor.execution.ExecutionContext;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.neuro.ThinkingRippleGenerator;
import com.thirdexploration.promengine.neuro.web.RippleWebSocketHandler;
import com.thirdexploration.promengine.prompt.core.PromptContext;
import com.thirdexploration.promengine.prompt.core.PromptPipeline;
import io.milvus.common.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * ReAct 模式编排器，适配 Spring AI M7。
 * 通过配置 promengine.orchestrator.mode=REACT 激活。
 */
@Component
@ConditionalOnProperty(name = "promengine.orchestrator.mode", havingValue = "REACT")
@Slf4j
@RequiredArgsConstructor
public class ReactOrchestrator implements Orchestrator {

    private final ChatClient.Builder chatClientBuilder;
    private final ToolExecutor toolExecutor;
    private final UnifiedMemoryAPI memoryAPI;
    private final OrchestratorProperties properties;
    private final PromptPipeline promptPipeline;
    private final RippleWebSocketHandler rippleHandler;

    @Value("${promengine.orchestrator.verbose-logging:false}")
    private boolean verboseLogging;

    @Value("${promengine.orchestrator.llm-retry-max:2}")
    private int llmRetryMax;

    @Value("${promengine.orchestrator.llm-retry-delay-ms:1000}")
    private long llmRetryDelayMs;



    private final Map<String, WindowedRipple> rippleWindows = new ConcurrentHashMap<>();

    private static class WindowedRipple {
        long lastSent = 0;
        double sumEntropy = 0;
        int count = 0;
    }

    private void sendRippleSampled(String sessionId, double entropy, String color) {
        long now = System.currentTimeMillis();
        WindowedRipple window = rippleWindows.computeIfAbsent(sessionId, k -> new WindowedRipple());

        window.sumEntropy += entropy;
        window.count++;

        // 每 500ms 或累积 10 个 token 时发送一次
        if ((now - window.lastSent > 1000) || window.count >= 20) {
            double avgEntropy = window.sumEntropy / window.count;
            rippleHandler.sendToSession(sessionId, new ThinkingRippleGenerator.RippleEvent("ripple",
                    avgEntropy,
                    color,
                    now
            ));
            window.lastSent = now;
            window.sumEntropy = 0;
            window.count = 0;
        }
    }



    @Override
    public CompletableFuture<Response> execute(ExecutionContext ctx) {

        // 从上下文获取 Agent 专属配置
        @SuppressWarnings("unchecked")
        Map<String, Object> agentConfig = (Map<String, Object>) ctx.getAttribute("agentConfig", Map.class);
        String systemPromptOverride = null;
        List<String> allowedTools = null;
        String memoryDomain = "general";
        if (agentConfig != null) {
            systemPromptOverride = (String) agentConfig.get("systemPrompt");
            allowedTools = (List<String>) agentConfig.get("tools");
            memoryDomain = (String) agentConfig.getOrDefault("memoryDomain", "general");
        }
        log.info("ReactOrchestrator (M7) started for session: {}", ctx.getUserInput().getSessionId());
        long startTime = System.currentTimeMillis();

        // 1. 通过管线构建系统提示词
        String systemPrompt;
        if (systemPromptOverride != null && !systemPromptOverride.isBlank()) {
            systemPrompt = systemPromptOverride;
        } else {
            TaskContext taskCtx = ctx.toTaskContext();
            taskCtx.setTaskType("react_conversation");
            PromptContext context = promptPipeline.collect(taskCtx);
            context.setAvailableTools(toolExecutor.getAvailableToolNames());
            context.setToolDescriptions(toolExecutor.getToolDescriptions());
            systemPrompt = promptPipeline.render(context);
            systemPrompt = promptPipeline.compress(systemPrompt);
        }

        // 2. 构建初始对话
        List<Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage(systemPrompt));
        conversation.add(new UserMessage(ctx.getUserInput().getText()));

        //构建一个干净的 ChatClient（不预注册任何工具）
        ChatClient chatClient = chatClientBuilder.build();

        int step = 0;
        String finalAnswer = null;
        String modelUsed = null;

        while (step < properties.getMaxSteps()) {
            step++;
            log.info("========== ReAct 第 {} 轮开始 ==========", step);
            logReActStep(step, conversation);
            ToolCallback[] tools = getToolCallbacks();
            if (allowedTools != null && !allowedTools.isEmpty()) {
                Set<String> allowedSet = new HashSet<>(allowedTools);
                tools = Arrays.stream(tools)
                        .filter(tc -> allowedSet.contains(tc.getName()))
                        .toArray(ToolCallback[]::new);
            }
            ChatResponse response = callLLMWithRetry(chatClient, conversation, tools, step);
            if (response == null) {
                finalAnswer = "抱歉，模型调用出现异常，请稍后重试。";
                break;
            }

            if (modelUsed == null && response.getMetadata() != null) {
                modelUsed = response.getMetadata().getModel();
            }

            // 提取助手消息
            Message assistantMessage = response.getResult().getOutput();
            conversation.add(assistantMessage);
            logLLMResponse(assistantMessage);

            log.info("工具调用判断打印：{}",hasToolCalls(assistantMessage));
            // 检查是否有工具调用
            if (hasToolCalls(assistantMessage) && properties.isToolUseEnabled()) {
                log.info("========== 第 {} 轮：检测到工具调用，执行工具 ==========", step);
                List<ToolResponseMessage.ToolResponse> toolResponses = executeToolCalls(assistantMessage);
                conversation.add(new ToolResponseMessage(toolResponses));
                logToolResponses(toolResponses);
                log.info("=== 工具执行完毕，继续下一轮对话让 LLM 生成最终回复 ===");
            } else {
                log.info("========== 第 {} 轮：无工具调用，生成最终回复 ==========", step);
                finalAnswer = cleanModelOutput(assistantMessage.getText());
                break;
            }

        }

        if (finalAnswer == null) {
            finalAnswer = "任务执行超过最大步数限制，已终止。";
        }

        logCompletion(step, finalAnswer, modelUsed);

        // 异步存储对话记忆，避免阻塞主流程
        storeConversationMemoryAsync(ctx, finalAnswer);

        long tookMs = System.currentTimeMillis() - startTime;
        log.info("ReactOrchestrator finished in {} steps, {} ms", step, tookMs);

        return CompletableFuture.completedFuture(
                Response.builder()
                        .text(finalAnswer)
                        .processingTimeMs(tookMs)
                        .modelUsed(modelUsed != null ? modelUsed : "unknown")
                        .cost(0.0)
                        .build()
        );
    }


    // ------------------- 流式执行（支持 Thinking 模式）-------------------
    @Override
    public Stream<CompletionChunk> executeStream(ExecutionContext ctx) {
        log.info("ReactOrchestrator stream started for session: {}", ctx.getUserInput().getSessionId());
        TaskContext taskCtx = ctx.toTaskContext();
        taskCtx.setTaskType("react_conversation");
        PromptContext promptCtx = promptPipeline.collect(taskCtx);
        promptCtx.setAvailableTools(toolExecutor.getAvailableToolNames());
        promptCtx.setToolDescriptions(toolExecutor.getToolDescriptions());
        String systemPrompt = promptPipeline.render(promptCtx);
        systemPrompt = promptPipeline.compress(systemPrompt);

        List<Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage(systemPrompt));
        conversation.add(new UserMessage(ctx.getUserInput().getText()));
        ChatClient chatClient = chatClientBuilder.build();

        BlockingQueue<CompletionChunk> queue = new LinkedBlockingQueue<>();
        final boolean[] finished = {false};
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                streamReActLoop(chatClient, conversation, ctx, queue, finished);
            } catch (Exception e) {
                log.error("Stream loop failed", e);
                queue.offer(CompletionChunk.builder().delta("流式处理异常: " + e.getMessage()).last(true).build());
            }
        });
        return StreamSupport.stream(
                new Spliterators.AbstractSpliterator<CompletionChunk>(Long.MAX_VALUE, 0) {
                    @Override
                    public boolean tryAdvance(java.util.function.Consumer<? super CompletionChunk> action) {
                        try {
                            CompletionChunk chunk = queue.poll();
                            if (chunk == null && !finished[0]) chunk = queue.take();
                            if (chunk != null) {
                                action.accept(chunk);
                                if (chunk.isLast()) finished[0] = true;
                                return true;
                            }
                            return false;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                }, false);
    }

    private void streamReActLoop(ChatClient chatClient, List<Message> conversation, ExecutionContext ctx,
                                 BlockingQueue<CompletionChunk> queue, boolean[] finished) {
        AtomicInteger step = new AtomicInteger(0);
        String[] modelUsed = {null};
        AtomicReference<String> finalAnswer = new AtomicReference<>();
        startNextRound(chatClient, conversation, queue, step, modelUsed, finalAnswer, finished, ctx);
    }

    private void startNextRound(ChatClient chatClient, List<Message> conversation,
                                BlockingQueue<CompletionChunk> queue, AtomicInteger step,
                                String[] modelUsed, AtomicReference<String> finalAnswer,
                                boolean[] finished, ExecutionContext ctx) {
        if (step.incrementAndGet() > properties.getMaxSteps()) {
            finishStream(queue, finalAnswer.get() != null ? finalAnswer.get() : "超过最大步数", finished, finalAnswer, ctx);
            return;
        }

        ToolCallback[] tools = getToolCallbacks();
        Flux<ChatResponse> flux = chatClient.prompt()
                .messages(conversation)
                .tools(tools)
                .options(ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build())
                .stream()
                .chatResponse();

        List<String> roundTexts = new ArrayList<>();
        AtomicBoolean hasToolCall = new AtomicBoolean(false);
        AtomicReference<AssistantMessage> lastAssistantMsg = new AtomicReference<>();

        flux.doOnNext(response -> {
            if (modelUsed[0] == null && response.getMetadata() != null) {
                modelUsed[0] = response.getMetadata().getModel();
            }
            Message msg = response.getResult().getOutput();
            if (msg instanceof AssistantMessage am) lastAssistantMsg.set(am);
            String text = msg.getText();
            // 处理 Thinking 内容：检查是否有 thinking 元数据
            String thinking = null;
            if (msg instanceof AssistantMessage am && am.getMetadata() != null) {
                thinking = (String) am.getMetadata().get("thinking");
            }
            if (thinking != null && !thinking.isEmpty()) {
                // 推送思考内容，前端用 [思考] 标记区分
                queue.offer(CompletionChunk.builder().delta("[思考] " + thinking).last(false).build());
                roundTexts.add("[思考] " + thinking);
            } else if (text != null) {
                String clean = cleanStreamText(text);
                if (!clean.isEmpty()) {
                    roundTexts.add(clean);
                    queue.offer(CompletionChunk.builder().delta(clean).last(false).build());

                    // 推送思维涟漪事件
                    String sessionId = ctx.getUserInput().getSessionId();
                    double entropy = Math.min(0.1 + Math.random() * 0.4, 0.5); // 可替换为真实计算
                    String color = entropy < 0.3 ? "green" : (entropy < 0.6 ? "orange" : "red");
                    sendRippleSampled(sessionId,entropy,color);

                }
            }
            if (hasToolCalls(msg)) hasToolCall.set(true);
        }).doOnComplete(() -> {

            String fullAssistant = String.join("", roundTexts);
            conversation.add(new AssistantMessage(fullAssistant));
            AssistantMessage lastMsg = lastAssistantMsg.get();


            if (lastMsg != null && hasToolCall.get() && properties.isToolUseEnabled()) {
                List<ToolResponseMessage.ToolResponse> toolResponses = executeToolCalls(lastMsg);
                for (var tr : toolResponses) {
                    queue.offer(CompletionChunk.builder().delta("[工具] " + tr.name() + " → " + tr.responseData()).last(false).build());
                }
                conversation.add(new ToolResponseMessage(toolResponses));
                String immediate = cleanModelOutput(lastMsg.getText());
                if (immediate != null && !immediate.isBlank() && !immediate.startsWith("call:")) {
                    finalAnswer.set(immediate);
                    finishStream(queue, immediate, finished, finalAnswer, ctx);
                } else {
                    startNextRound(chatClient, conversation, queue, step, modelUsed, finalAnswer, finished, ctx);
                }
            } else {
//                String answer = cleanModelOutput(lastMsg != null ? lastMsg.getText() : fullAssistant);
//                finalAnswer.set(answer);
//                finishStream(queue, answer, finished, finalAnswer, ctx);
                String answer = cleanModelOutput(lastMsg != null ? lastMsg.getText() : fullAssistant);
                if (answer == null || answer.isBlank()) {
                    answer = fullAssistant; // fallback
                }
                finalAnswer.set(answer);
                finishStream(queue, answer, finished, finalAnswer, ctx);
            }
        }).doOnError(e -> {
            log.error("Stream error", e);
            finishStream(queue, "流式错误: " + e.getMessage(), finished, finalAnswer, ctx);
        }).subscribe();
    }

    private void finishStream(BlockingQueue<CompletionChunk> queue, String answer, boolean[] finished,
                              AtomicReference<String> finalAnswer, ExecutionContext ctx) {
        queue.offer(CompletionChunk.builder().delta("").last(true).build());
        finished[0] = true;
        String validAnswer = (answer != null && !answer.isBlank()) ? answer : finalAnswer.get();
        if (validAnswer == null || validAnswer.isBlank()) {
            validAnswer = "模型未返回有效内容";
        }
        storeConversationMemoryAsync(ctx, validAnswer);
    }

    private void storeConversationMemoryAsync(ExecutionContext ctx, String finalAnswer) {
        CompletableFuture.runAsync(() -> {
            try {
                if (finalAnswer == null || finalAnswer.isBlank()) {
                    log.warn("Empty answer, skip memory storage");
                    return;
                }
                String userId = ctx.getUserId() != null && !ctx.getUserId().isBlank() ? ctx.getUserId() : "default-user";
                MemoryEntry entry = MemoryEntry.builder()
                        .userId(userId)
                        .content("用户: " + ctx.getUserInput().getText() + "\n助手: " + finalAnswer)
                        .timestamp(Instant.now())
                        .memoryType("EPISODIC")
                        .importance(0.6f)
                        .domain("general")
                        .layer("episodic")
                        .strength(1.0f)
                        .sharingLevel("private")
                        .build();
                memoryAPI.remember(entry);
                log.debug("Async memory stored for session {}", ctx.getUserInput().getSessionId());
            } catch (Exception e) {
                log.warn("Failed to store conversation memory asynchronously", e);
            }
        });
    }

    // ---------- 文本清理（保留思考标记）----------
    private String cleanStreamText(String raw) {
        if (raw == null) return "";
        // 只移除工具调用标记，保留 thought 等思考文字
        return raw.replaceAll("call:\\w+\\{[^}]*\\}", "")
                .replaceAll("<tool_call\\|>|<\\|tool_response>", "");
    }

    private String cleanModelOutput(String text) {
        if (text == null) return "";
        // 移除 thought 标记但保留内容作为最终回复？最终回复应当包含思考内容，所以我们只移除多余的标记
        return text.replaceAll("call:\\w+\\{[^}]*\\}", "")
                .replaceAll("<tool_call\\|>|<\\|tool_response>", "")
                .replaceAll("}", "")
                .trim();
    }

    // ------------------- LLM 调用与重试 -------------------

    private ChatResponse callLLMWithRetry(ChatClient chatClient, List<Message> conversation,
                                          ToolCallback[] tools, int step) {
        Exception lastException = null;
        for (int retry = 0; retry <= llmRetryMax; retry++) {
            try {
                return chatClient.prompt()
                        .messages(conversation)
                        .tools(tools)
                        .options(ToolCallingChatOptions.builder()
                                .internalToolExecutionEnabled(false) // 禁用内部自动工具执行,promengine的R-CCAM 模式需要禁用spring ai的 react实现
                                .build())
                        .call()
                        .chatResponse();
            } catch (ResourceAccessException e) {
                // 重试逻辑保持不变
                lastException = e;
                if (retry < llmRetryMax) {
                    log.warn("LLM call timeout at step {}, retrying {}/{} after {} ms",
                            step, retry + 1, llmRetryMax, llmRetryDelayMs);
                    try {
                        Thread.sleep(llmRetryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                lastException = e;
                break;
            }
        }
        log.error("LLM call failed at step {} after {} retries", step, llmRetryMax, lastException);
        return null;
    }

    // ------------------- 工具调用处理 -------------------

    private List<ToolResponseMessage.ToolResponse> executeToolCalls(Message assistantMessage) {
        List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
        for (AssistantMessage.ToolCall toolCall : extractToolCalls(assistantMessage)) {
            log.info("Executing tool: {} with args: {}", toolCall.name(), toolCall.arguments());
            String toolResult = toolExecutor.execute(toolCall);
            toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), toolResult));
        }
        return toolResponses;
    }

    private ToolCallback[] getToolCallbacks() {
        if (!properties.isToolUseEnabled()) {
            return new ToolCallback[0];
        }
        List<ToolCallback> all = toolExecutor.getAvailableTools();
        // 使用 LinkedHashMap 保持顺序的同时按名称去重
        Map<String, ToolCallback> uniqueMap = new LinkedHashMap<>();
        for (ToolCallback cb : all) {
            uniqueMap.putIfAbsent(cb.getName(), cb);
        }
        ToolCallback[] callbacks = uniqueMap.values().toArray(new ToolCallback[0]);
        if (verboseLogging) {
            log.info("=== 可用工具列表 ({} 个，已去重) ===", callbacks.length);
            for (ToolCallback cb : callbacks) {
                log.info("  - {}: {}", cb.getToolDefinition().name(), cb.getToolDefinition().description());
            }
        }
        return callbacks;
    }

    private boolean hasToolCalls(Message message) {
        return message instanceof AssistantMessage am && am.hasToolCalls();
    }

    private List<AssistantMessage.ToolCall> extractToolCalls(Message message) {
        return message instanceof AssistantMessage am ? am.getToolCalls() : List.of();
    }


    // ------------------- 观测日志辅助方法 -------------------

    private void logReActStep(int step, List<Message> conversation) {
        if (!verboseLogging) return;
        log.debug("ReAct step {}/{}", step, properties.getMaxSteps());
        log.info("=== ReAct 第 {} 轮对话历史 ({} 条消息) ===", step, conversation.size());
        for (int i = 0; i < conversation.size(); i++) {
            Message msg = conversation.get(i);
            String role = msg.getMessageType().name();
            String content = msg.getText();
            if (content != null && content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            log.info("  [{}] {}: {}", i, role, content);
        }
    }


    private void logLLMResponse(Message assistantMessage) {
        if (!verboseLogging) return;
        if (hasToolCalls(assistantMessage)) {
            List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(assistantMessage);
            log.info("=== LLM 请求调用工具 ({} 个) ===", toolCalls.size());
            for (AssistantMessage.ToolCall tc : toolCalls) {
                log.info("  工具名: {}", tc.name());
                log.info("  参数: {}", tc.arguments());
            }
        } else {
            String text = assistantMessage.getText();
            String preview = text.length() > 300 ? text.substring(0, 300) + "..." : text;
            log.info("=== LLM 直接回复 (无工具调用) ===\n{}", preview);
        }
    }

    private void logToolResponses(List<ToolResponseMessage.ToolResponse> toolResponses) {
        if (!verboseLogging) return;
        log.info("=== 工具执行结果已反馈给 LLM ===");
        for (ToolResponseMessage.ToolResponse tr : toolResponses) {
            String preview = tr.responseData().length() > 200
                    ? tr.responseData().substring(0, 200) + "..."
                    : tr.responseData();
            log.info("  工具 {} 返回: {}", tr.name(), preview);
        }
    }

    private void logCompletion(int step, String finalAnswer, String modelUsed) {
        log.info("=== ReAct 执行完成 ===");
        log.info("  总步数: {}", step);
        log.info("  最终回复: {}", finalAnswer);
        log.info("  模型: {}", modelUsed);
    }
}