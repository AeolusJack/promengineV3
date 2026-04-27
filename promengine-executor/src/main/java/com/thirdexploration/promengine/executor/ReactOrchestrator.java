package com.thirdexploration.promengine.executor;

import com.nimbusds.jose.shaded.json.JSONObject;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jose.util.JSONStringUtils;
import com.thirdexploration.promengine.core.domain.*;
import com.thirdexploration.promengine.executor.config.OrchestratorProperties;
import com.thirdexploration.promengine.executor.execution.ExecutionContext;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
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

    @Value("${promengine.orchestrator.verbose-logging:false}")
    private boolean verboseLogging;

    @Value("${promengine.orchestrator.llm-retry-max:2}")
    private int llmRetryMax;

    @Value("${promengine.orchestrator.llm-retry-delay-ms:1000}")
    private long llmRetryDelayMs;

    @Override
    public CompletableFuture<Response> execute(ExecutionContext ctx) {
        log.info("ReactOrchestrator (M7) started for session: {}", ctx.getUserInput().getSessionId());
        long startTime = System.currentTimeMillis();

        // 1. 通过管线构建系统提示词
        TaskContext taskCtx = ctx.toTaskContext();
        taskCtx.setTaskType("react_conversation");
        PromptContext context = promptPipeline.collect(taskCtx);
        context.setAvailableTools(toolExecutor.getAvailableToolNames());
        context.setToolDescriptions(toolExecutor.getToolDescriptions());
        String systemPrompt = promptPipeline.render(context);
        systemPrompt = promptPipeline.compress(systemPrompt);

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

    @Override
    public Stream<CompletionChunk> executeStream(ExecutionContext ctx) {
        log.info("ReactOrchestrator stream started for session: {}", ctx.getUserInput().getSessionId());

        // 初始化上下文和对话
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

        // 使用阻塞队列实现实时流式传输
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
                            if (chunk == null && !finished[0]) {
                                chunk = queue.take(); // 阻塞等待
                            }
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

        // 使用 AtomicReference 保存最终答案
        AtomicReference<String> finalAnswer = new AtomicReference<>();

        // 启动首轮
        startNextRound(chatClient, conversation, queue, step, modelUsed, finalAnswer, finished, ctx);
    }

    private void startNextRound(ChatClient chatClient, List<Message> conversation,
                                BlockingQueue<CompletionChunk> queue, AtomicInteger step,
                                String[] modelUsed, AtomicReference<String> finalAnswer,
                                boolean[] finished, ExecutionContext ctx) {
        if (step.incrementAndGet() > properties.getMaxSteps()) {
            finishStream(queue, "超过最大步数", finished, finalAnswer, ctx);
            return;
        }


        // 构建带有 think 参数的 ChatClient
//        ChatClient chatClient = chatClientBuilder
//                .defaultOptions(OllamaOptions.builder()
//                        .model("gemma4-custom:q4")  // 或其他支持 think 的模型
//                        .withCustomOption("think", true)  // 启用思考模式
//                        .build())
//                .build();
//
        ToolCallback[] tools = getToolCallbacks();
        Flux<ChatResponse> flux = chatClient.prompt()
                .messages(conversation)
                .tools(tools)
                .options(ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build())
                .stream()
                .chatResponse();

        // 收集本轮文本用于构建完整助手消息
        List<String> roundTexts = new ArrayList<>();
        AtomicBoolean hasToolCall = new AtomicBoolean(false);
        AtomicReference<AssistantMessage> lastAssistantMsg = new AtomicReference<>();

        flux.doOnNext(response -> {
            if (modelUsed[0] == null && response.getMetadata() != null) {
                modelUsed[0] = response.getMetadata().getModel();
            }
            Message msg = response.getResult().getOutput();
            List<Generation> results = response.getResults();
            ChatResponseMetadata metadata = response.getMetadata();
            lastAssistantMsg.set(msg instanceof AssistantMessage ? (AssistantMessage) msg : null);
            String text = msg.getText();
//            log.debug("调试显示 Stream token: {},results 内容：{} ,metadata内容{} ", text,results,metadata); // 添加这行，显示每个 token
            if (text != null) {
                String clean = cleanStreamText(text);
                if (!clean.isEmpty()) {
                    roundTexts.add(clean);
                    queue.offer(CompletionChunk.builder().delta(clean).last(false).build());
                }
            }
            if (hasToolCalls(msg)) {
                hasToolCall.set(true);
            }
        }).doOnComplete(() -> {
            // 本轮结束
            String fullAssistant = String.join("", roundTexts);
            conversation.add(new AssistantMessage(fullAssistant));
            AssistantMessage lastMsg = lastAssistantMsg.get();
            if (lastMsg != null && hasToolCall.get() && properties.isToolUseEnabled()) {
                List<ToolResponseMessage.ToolResponse> toolResponses = executeToolCalls(lastMsg);
                for (ToolResponseMessage.ToolResponse tr : toolResponses) {
                    queue.offer(CompletionChunk.builder()
                            .delta("[工具] " + tr.name() + " → " + tr.responseData()).last(false).build());
                }
                conversation.add(new ToolResponseMessage(toolResponses));
                String immediate = cleanModelOutput(lastMsg.getText());
                if (immediate != null && !immediate.isBlank() && !immediate.startsWith("call:")) {
                    finalAnswer.set(immediate);
                    finishStream(queue, immediate, finished, finalAnswer, ctx);
                } else {
                    // 继续下一轮
                    startNextRound(chatClient, conversation, queue, step, modelUsed, finalAnswer, finished, ctx);
                }
            } else {
                String answer = cleanModelOutput(lastMsg != null ? lastMsg.getText() : fullAssistant);
                finalAnswer.set(answer);
                finishStream(queue, answer, finished, finalAnswer, ctx);
            }
        }).doOnError(e -> {
            log.error("Stream error", e);
            finishStream(queue, "流式错误: " + e.getMessage(), finished, finalAnswer, ctx);
        }).subscribe(); // 关键：非阻塞订阅，让流式持续输出
    }


    private void finishStream(BlockingQueue<CompletionChunk> queue, String answer, boolean[] finished,
                              AtomicReference<String> finalAnswer, ExecutionContext ctx) {
        queue.offer(CompletionChunk.builder().delta("").last(true).build());
        finished[0] = true;
        storeConversationMemoryAsync(ctx, answer != null ? answer : finalAnswer.get());
    }

    private void storeConversationMemoryAsync(ExecutionContext ctx, String finalAnswer) {
        CompletableFuture.runAsync(() -> {
            try {
                String userId = ctx.getUserId();
                if (userId == null || userId.isBlank()) {
                    userId = "default-user";
                }
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
            } catch (Exception e) {
                log.warn("Failed to store conversation memory asynchronously", e);
            }
        });
    }



    private String cleanStreamText(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("call:\\w+\\{[^}]*\\}", "")
                .replaceAll("<tool_call\\|>|<\\|tool_response>", "");
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

    // ------------------- 异步记忆存储 -------------------

//    private void storeConversationMemoryAsync(ExecutionContext ctx, String finalAnswer) {
//        CompletableFuture.runAsync(() -> {
//            try {
//                MemoryEntry entry = MemoryEntry.builder()
//                        .userId(ctx.getUserId())
//                        .content("用户: " + ctx.getUserInput().getText() + "\n助手: " + finalAnswer)
//                        .timestamp(Instant.now())
//                        .memoryType("EPISODIC")
//                        .importance(0.6f)
//                        .domain("general")
//                        .layer("episodic")
//                        .strength(1.0f)
//                        .sharingLevel("private")
//                        .build();
//                memoryAPI.remember(entry);
//                log.debug("Conversation memory stored asynchronously for session {}",
//                        ctx.getUserInput().getSessionId());
//            } catch (Exception e) {
//                log.warn("Failed to store conversation memory asynchronously", e);
//            }
//        });
//    }

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
    private String cleanModelOutput(String text) {
        if (text == null) return "";
        // 移除 "thought" 前缀（可能有换行和空格）
        String cleaned = text.replaceFirst("(?i)^\\s*thought\\s*", "");
        // 移除 "<channel|>" 标记
        cleaned = cleaned.replaceAll("<channel\\|>", "");
        cleaned =  cleaned.replace("}", "");
        return cleaned.trim();
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