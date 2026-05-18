package com.thirdexploration.promengine.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.AgentConfig;
import com.thirdexploration.promengine.core.agent.*;
import com.thirdexploration.promengine.core.cache.StreamFragmentStore;
import com.thirdexploration.promengine.core.context.ConversationContext;
import com.thirdexploration.promengine.core.context.ConversationContextBuilder;
import com.thirdexploration.promengine.core.domain.*;
import com.thirdexploration.promengine.core.trace.TraceContext;
import com.thirdexploration.promengine.executor.config.OrchestratorProperties;
import com.thirdexploration.promengine.executor.event.StreamCompletedEvent;
import com.thirdexploration.promengine.executor.execution.ExecutionContext;
import com.thirdexploration.promengine.executor.tool.registry.ToolRegistry;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.agent.model.ReActStepRecord;
import com.thirdexploration.promengine.memory.agent.repository.ReActStepRepository;
import com.thirdexploration.promengine.neuro.ThinkingRippleGenerator;
import com.thirdexploration.promengine.neuro.web.RippleWebSocketHandler;
import com.thirdexploration.promengine.prompt.core.PromptContext;
import com.thirdexploration.promengine.prompt.core.PromptPipeline;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor
@Slf4j
@Component
@ConditionalOnProperty(name = "promengine.orchestrator.mode", havingValue = "REACT")
public class ReactOrchestrator implements Orchestrator {

    private final ChatClient.Builder chatClientBuilder;
    private final ToolExecutor toolExecutor;
    private final UnifiedMemoryAPI memoryAPI;
    private final OrchestratorProperties properties;
    private final PromptPipeline promptPipeline;
    private final RippleWebSocketHandler rippleHandler;
    private final AgentConfigProvider agentConfigProvider;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final ReActStepRepository stepRepository;
    private final StreamFragmentStore streamFragmentStore;
    private final ApplicationEventPublisher eventPublisher;
    private final ReviewService reviewHandler;
    private final ChatHistoryProvider chatHistoryProvider;   // 通过构造器注入
    private final ConversationContextBuilder conversationContextBuilder;


    @Autowired(required = false)
    private TaskPlanningStrategy planningStrategy;

    @Value("${promengine.orchestrator.verbose-logging:false}")
    private boolean verboseLogging;

    @Value("${promengine.orchestrator.llm-retry-max:2}")
    private int llmRetryMax;

    @Value("${promengine.orchestrator.llm-retry-delay-ms:1000}")
    private long llmRetryDelayMs;

    @Value("${promengine.orchestrator.stream-timeout-seconds:1200}")
    private long streamTimeoutSeconds;

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
        if ((now - window.lastSent > 1000) || window.count >= 20) {
            double avgEntropy = window.sumEntropy / window.count;
            rippleHandler.sendToSession(sessionId, new ThinkingRippleGenerator.RippleEvent("ripple",
                    avgEntropy, color, now));
            window.lastSent = now;
            window.sumEntropy = 0;
            window.count = 0;
        }
    }

    private String applyCriticIfNeeded(String candidateAnswer, List<Message> conversation,
                                       ChatClient chatClient, ExecutionContext ctx) {
        if (!properties.isCriticEnabled()) return candidateAnswer;
        if (candidateAnswer == null || candidateAnswer.isBlank()) return candidateAnswer;

        // 构建 Critic Prompt：让模型审查自己的回答
        String criticPrompt = String.format(
                "你是一个严格的审查员。请检查以下回答是否存在事实错误、逻辑矛盾或未完成的部分。\n" +
                        "如果回答完美，请回复 \"PASS\"。\n" +
                        "如果发现问题，请指出问题并给出修正后的完整回答。\n\n" +
                        "【原始回答】\n%s\n\n【审查意见】", candidateAnswer);

        try {
            ChatResponse response = chatClient.prompt()
                    .user(criticPrompt)
                    .call()
                    .chatResponse();
            String criticOutput = response.getResult().getOutput().getText();
            if (criticOutput == null || criticOutput.isBlank()) return candidateAnswer;

            // 若模型返回 PASS，则保留原答案
            if (criticOutput.trim().equalsIgnoreCase("PASS")) {
                return candidateAnswer;
            }
            // 否则使用审查后的回答
            log.info("Critic found issues, using revised answer.");
            return criticOutput.trim();
        } catch (Exception e) {
            log.warn("Critic step failed, using original answer", e);
            return candidateAnswer;
        }
    }
    @Override
    public CompletableFuture<Response> execute(ExecutionContext ctx) {
        String agentId = ctx.getAttribute("agentId", String.class);
        AgentConfig agentConfig = agentId != null ? agentConfigProvider.getConfig(agentId) : null;
        String sessionId = ctx.getUserInput().getSessionId();
        TraceContext.setTraceId(ctx.getTraceId()); // 传播 traceId
        log.info("ReactOrchestrator (M7) started for session: {}", sessionId);
        long startTime = System.currentTimeMillis();

        String systemPrompt = buildSystemPrompt(ctx, agentConfig);
        String planText = generatePlanIfNeeded(ctx, agentConfig);
        if (!planText.isEmpty()) {
            systemPrompt += "\n\n" + planText;
        }

        List<Message> conversation = new ArrayList<>();
        // 构建三层上下文
        ConversationContext context = conversationContextBuilder.buildContext(
                ctx.getUserInput().getSessionId(),
                properties.getContextWindowSize()); // 窗口大小从配置读取

        String enhancedSystemPrompt = systemPrompt;
        if (context != null) {
            String ctxSection = context.toPromptSection();
            if (!ctxSection.isBlank()) {
                enhancedSystemPrompt = systemPrompt + "\n\n" + ctxSection;
            }
        }
        conversation.add(new SystemMessage(enhancedSystemPrompt));
        conversation.add(new UserMessage(ctx.getUserInput().getText()));

        ChatClient chatClient = chatClientBuilder.build();

        String finalAnswer = null;
        String modelUsed = null;
        String memoryDomain = agentConfig != null ? agentConfig.getMemoryDomain() : "general";

        try {
            while (ctx.getStepCounter().get() < properties.getMaxSteps()) {
                int currentStep = ctx.nextStepNumber();
                log.info("========== ReAct 第 {} 轮开始 ==========", currentStep);
                logReActStep(currentStep, conversation);

                pushAndSaveStep(sessionId, agentId, ReActStepEvent.builder()
                        .type("THINKING")
                        .executionId(ctx.getExecutionId())
                        .stepNumber(currentStep)
                        .description("模型推理中...")
                        .status("RUNNING")
                        .timestamp(System.currentTimeMillis())
                        .build());

                ToolCallback[] tools = resolveToolCallbacks(agentConfig);
                ChatResponse response = callLLMWithRetry(chatClient, conversation, tools, currentStep);
                if (response == null) {
                    finalAnswer = "抱歉，模型调用出现异常，请稍后重试。";
                    break;
                }

                if (modelUsed == null && response.getMetadata() != null) {
                    modelUsed = response.getMetadata().getModel();
                }

                Message assistantMessage = response.getResult().getOutput();
                conversation.add(assistantMessage);
                logLLMResponse(assistantMessage);

                if (hasToolCalls(assistantMessage) && properties.isToolUseEnabled()) {
                    log.info("========== 第 {} 轮：检测到工具调用，执行工具 ==========", currentStep);
                    List<ToolResponseMessage.ToolResponse> toolResponses =
                            executeToolCalls(assistantMessage, currentStep, ctx, agentId);
                    conversation.add(new ToolResponseMessage(toolResponses));
                    logToolResponses(toolResponses);
                } else {
                    log.info("========== 第 {} 轮：无工具调用，生成最终回复 ==========", currentStep);
                    finalAnswer = cleanModelOutput(assistantMessage.getText());
                    break;
                }
            }

            if (finalAnswer == null) {
                finalAnswer = "任务执行超过最大步数限制，已终止。";
            }
            // ===== 新增：Critic 审查 =====
            finalAnswer = applyCriticIfNeeded(finalAnswer, conversation, chatClient, ctx);
             // ===== 结束 =====
        } finally {
            if (finalAnswer != null) {
                storeConversationMemoryAsync(ctx, finalAnswer, memoryDomain);
            }
            TraceContext.clear();
        }

        logCompletion(ctx.getStepCounter().get(), finalAnswer, modelUsed);

        long tookMs = System.currentTimeMillis() - startTime;
        log.info("ReactOrchestrator finished in {} steps, {} ms", ctx.getStepCounter(), tookMs);

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
        TraceContext.setTraceId(ctx.getTraceId());
        String agentId = ctx.getAttribute("agentId", String.class);
        AgentConfig agentConfig = agentId != null ? agentConfigProvider.getConfig(agentId) : null;
        String sessionId = ctx.getUserInput().getSessionId();
        log.info("ReactOrchestrator stream started for session: {}", sessionId);

        String systemPrompt = buildSystemPrompt(ctx, agentConfig);
        String planText = generatePlanIfNeeded(ctx, agentConfig);
        if (!planText.isEmpty()) {
            systemPrompt += "\n\n" + planText;
        }

        List<Message> conversation = new ArrayList<>();
        // 构建三层上下文
        ConversationContext context = conversationContextBuilder.buildContext(
                ctx.getUserInput().getSessionId(),
                properties.getContextWindowSize()); // 窗口大小从配置读取

        String enhancedSystemPrompt = systemPrompt;
        if (context != null) {
            String ctxSection = context.toPromptSection();
            if (!ctxSection.isBlank()) {
                enhancedSystemPrompt = systemPrompt + "\n\n" + ctxSection;
            }
        }
        conversation.add(new SystemMessage(enhancedSystemPrompt));
        conversation.add(new UserMessage(ctx.getUserInput().getText()));
        ChatClient chatClient = chatClientBuilder.build();

        BlockingQueue<CompletionChunk> queue = new LinkedBlockingQueue<>();
        final boolean[] finished = {false};
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                streamReActLoop(chatClient, conversation, ctx, queue, finished, agentConfig);
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
                            // 移除主动超时，改为无限等待（除非流已标记完成）
                            if (finished[0]) return false;
                            CompletionChunk chunk = queue.take();  // 阻塞等待
                            action.accept(chunk);
                            if (chunk.isLast()) finished[0] = true;
                            return true;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                }, false).onClose(TraceContext::clear);
    }

    private void streamReActLoop(ChatClient chatClient, List<Message> conversation, ExecutionContext ctx,
                                 BlockingQueue<CompletionChunk> queue, boolean[] finished, AgentConfig agentConfig) {
        String[] modelUsed = {null};
        AtomicReference<String> finalAnswer = new AtomicReference<>();
        String memoryDomain = agentConfig != null ? agentConfig.getMemoryDomain() : "general";
        try {
            startNextRound(chatClient, conversation, queue, ctx, modelUsed, finalAnswer, finished, agentConfig);
        } finally {
            if (finalAnswer.get() != null) {
                storeConversationMemoryAsync(ctx, finalAnswer.get(), memoryDomain);
            }
            //todo 需要做
               //applyCriticIfNeeded("",conversation,chatClient,ctx);
        }
    }

    private void startNextRound(ChatClient chatClient, List<Message> conversation,
                                BlockingQueue<CompletionChunk> queue, ExecutionContext ctx,
                                String[] modelUsed, AtomicReference<String> finalAnswer,
                                boolean[] finished, AgentConfig agentConfig) {
        int currentStep = ctx.nextStepNumber();
        if (currentStep > properties.getMaxSteps()) {
            finishStream(queue, finalAnswer.get() != null ? finalAnswer.get() : "超过最大步数",
                    finished, finalAnswer, ctx, agentConfig);
            return;
        }

        String sessionId = ctx.getUserInput().getSessionId();
        String agentId = ctx.getAttribute("agentId", String.class);

        pushAndSaveStep(sessionId, agentId, ReActStepEvent.builder()
                .type("THINKING")
                .stepNumber(currentStep)
                .executionId(ctx.getExecutionId())
                .description("模型推理中...")
                .status("RUNNING")
                .timestamp(System.currentTimeMillis())
                .build());

        ToolCallback[] tools = (agentConfig != null) ? resolveToolCallbacks(agentConfig) : getToolCallbacks();

        Flux<ChatResponse> flux = chatClient.prompt()
                .messages(conversation)
                .tools(tools)
                .options(ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build())
                .stream()
                .chatResponse()
                .timeout(Duration.ofSeconds(streamTimeoutSeconds))
                .onErrorResume(TimeoutException.class, e -> {
                    log.warn("Stream timeout after {} seconds, terminating", streamTimeoutSeconds);
                    queue.offer(CompletionChunk.builder()
                            .delta("任务执行超时（" + streamTimeoutSeconds + "秒），请稍后重试。")
                            .last(true)
                            .build());
                    finished[0] = true;
                    return Flux.empty();
                });

        List<String> roundTexts = new ArrayList<>();
        AtomicReference<Boolean> hasToolCall = new AtomicReference<>(false);
        AtomicReference<AssistantMessage> lastAssistantMsg = new AtomicReference<>();
        final int step = currentStep;

        flux.doOnNext(response -> {
            if (modelUsed[0] == null && response.getMetadata() != null) {
                modelUsed[0] = response.getMetadata().getModel();
            }
            Message msg = response.getResult().getOutput();
            if (msg instanceof AssistantMessage am) lastAssistantMsg.set(am);
            String text = msg.getText();
            String thinking = null;
            if (msg instanceof AssistantMessage am && am.getMetadata() != null) {
                thinking = (String) am.getMetadata().get("thinking");
            }
            if (thinking != null && !thinking.isEmpty()) {
                queue.offer(CompletionChunk.builder().delta("[思考] " + thinking).last(false).build());
                roundTexts.add("[思考] " + thinking);
                streamFragmentStore.addFragment(ctx.getExecutionId(), "[思考] " + thinking);
            } else if (text != null) {
                String clean = cleanStreamText(text);
                if (!clean.isEmpty()) {
                    roundTexts.add(clean);
                    queue.offer(CompletionChunk.builder().delta(clean).last(false).build());
                    streamFragmentStore.addFragment(ctx.getExecutionId(), clean);
                    double entropy = Math.min(0.1 + Math.random() * 0.4, 0.5);
                    String color = entropy < 0.3 ? "green" : (entropy < 0.6 ? "orange" : "red");
                    sendRippleSampled(sessionId, entropy, color);
                }
            }
            if (hasToolCalls(msg)) hasToolCall.set(true);
        }).doOnComplete(() -> {
            String fullAssistant = String.join("", roundTexts);
            conversation.add(new AssistantMessage(fullAssistant));
            AssistantMessage lastMsg = lastAssistantMsg.get();

            if (lastMsg != null && hasToolCall.get() && properties.isToolUseEnabled()) {
                // 工具调用分支（保持不变）
                for (AssistantMessage.ToolCall tc : extractToolCalls(lastMsg)) {
                    pushAndSaveStep(sessionId, agentId, ReActStepEvent.builder()
                            .type("TOOL_CALL")
                            .stepNumber(step)
                            .executionId(ctx.getExecutionId())
                            .description("调用工具: " + tc.name())
                            .detail(tc.arguments())
                            .status("RUNNING")
                            .timestamp(System.currentTimeMillis())
                            .build());
                }
                List<ToolResponseMessage.ToolResponse> toolResponses =
                        executeToolCalls(lastMsg, step, ctx, agentId);
                for (var tr : toolResponses) {
                    pushAndSaveStep(sessionId, agentId, ReActStepEvent.builder()
                            .type("TOOL_RESULT")
                            .stepNumber(step)
                            .executionId(ctx.getExecutionId())
                            .description("工具返回: " + tr.name())
                            .detail(tr.responseData())
                            .status("SUCCESS")
                            .timestamp(System.currentTimeMillis())
                            .build());
                    queue.offer(CompletionChunk.builder()
                            .delta("[工具] " + tr.name() + " → " + tr.responseData()).last(false).build());
                    streamFragmentStore.addFragment(ctx.getExecutionId(), "[工具] " + tr.name() + " → " + tr.responseData());
                }
                conversation.add(new ToolResponseMessage(toolResponses));
                String immediate = cleanModelOutput(lastMsg.getText());
                if (immediate != null && !immediate.isBlank() && !immediate.startsWith("call:")) {
                    String reviewedImmediate = applyCriticIfNeeded(immediate, conversation, chatClient, ctx);
                    finalAnswer.set(reviewedImmediate);
                    finishStream(queue, reviewedImmediate, finished, finalAnswer, ctx, agentConfig);
                } else {
                    startNextRound(chatClient, conversation, queue, ctx, modelUsed, finalAnswer, finished, agentConfig);
                }
            } else {
                // 无工具调用分支
                String answer = null;
                if (!fullAssistant.isBlank()) {
                    answer = fullAssistant;
                } else if (lastMsg != null && lastMsg.getText() != null && !lastMsg.getText().isBlank()) {
                    answer = cleanModelOutput(lastMsg.getText());
                }
                if (answer == null || answer.isBlank()) {
                    answer = "模型未生成任何文本回复，可能仅执行了工具调用。";
                    queue.offer(CompletionChunk.builder().delta(answer).last(false).build());
                    streamFragmentStore.addFragment(ctx.getExecutionId(), answer);
                }
                String reviewedAnswer = applyCriticIfNeeded(answer, conversation, chatClient, ctx);
                finalAnswer.set(reviewedAnswer);
                finishStream(queue, reviewedAnswer, finished, finalAnswer, ctx, agentConfig);
            }
        }).doOnError(e -> {
            log.error("Stream error", e);
            if (finished[0]) return;
            pushAndSaveStep(sessionId, agentId, ReActStepEvent.builder()
                    .type("ERROR")
                    .executionId(ctx.getExecutionId())
                    .stepNumber(step)
                    .description("流式错误")
                    .detail(e.getMessage())
                    .status("FAILED")
                    .timestamp(System.currentTimeMillis())
                    .build());
            finishStream(queue, "流式错误: " + e.getMessage(), finished, finalAnswer, ctx, agentConfig);
        }).subscribe();
    }


    private void finishStream(BlockingQueue<CompletionChunk> queue, String answer, boolean[] finished, AtomicReference<String> finalAnswer, ExecutionContext ctx, AgentConfig agentConfig) {
        if (finished[0]) return;
        finished[0] = true;
        String validAnswer = (answer != null && !answer.isBlank()) ? answer : finalAnswer.get();
        if (validAnswer == null || validAnswer.isBlank()) {
            validAnswer = "任务执行完成，未生成文本回复。";
        }
        // 只发送最终内容（非 last），然后发送一个空内容且 last=true 的 chunk 表示结束
        queue.offer(CompletionChunk.builder().delta(validAnswer).last(false).build());
        queue.offer(CompletionChunk.builder().delta("").last(true).build());
        streamFragmentStore.markCompleted(ctx.getExecutionId());
        eventPublisher.publishEvent(new StreamCompletedEvent(this,
                ctx.getExecutionId(),
                ctx.getUserInput().getSessionId(),
                ctx.getUserId() != null ? ctx.getUserId() : "default-user",
                validAnswer));
        // 步骤事件及记忆存储保持不变
        pushAndSaveStep(ctx.getUserInput().getSessionId(), ctx.getAttribute("agentId", String.class),
                ReActStepEvent.builder()
                        .type("COMPLETE")
                        .executionId(ctx.getExecutionId())
                        .stepNumber(ctx.getStepCounter().get())
                        .description("执行完成")
                        .status("SUCCESS")
                        .timestamp(System.currentTimeMillis())
                        .build());
        String memoryDomain = (agentConfig != null) ? agentConfig.getMemoryDomain() : "general";
        storeConversationMemoryAsync(ctx, validAnswer, memoryDomain);
    }

    // ==================== 事件推送与持久化 ====================
    private void pushAndSaveStep(String sessionId, String agentId, ReActStepEvent event) {
        pushStepEvent(sessionId, event);
        CompletableFuture.runAsync(() -> {
            try {
                ReActStepRecord record = ReActStepRecord.builder()
                        .id(UUID.randomUUID().toString())
                        .agentId(agentId)
                        .sessionId(sessionId)
                        .executionId(event.getExecutionId())
                        .stepNumber(event.getStepNumber())
                        .type(event.getType())
                        .description(event.getDescription())
                        .detail(event.getDetail())
                        .status(event.getStatus())
                        .timestamp(event.getTimestamp())
                        .build();
                stepRepository.save(record);
            } catch (Exception e) {
                log.warn("存储步骤事件失败", e);
            }
        });
    }

    private void pushStepEvent(String sessionId, ReActStepEvent event) {
        if (sessionId == null) return;
        try {
            Map<String, Object> message = Map.of(
                    "type", "react_step",
                    "executionId", event.getExecutionId(),
                    "data", event
            );
            rippleHandler.sendToSession(sessionId, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            log.warn("推送 ReActStepEvent 失败", e);
        }
    }



    private List<ToolResponseMessage.ToolResponse> executeToolCalls(Message assistantMessage, int currentStep, ExecutionContext ctx, String agentId) {
        List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
        String sessionId = ctx.getUserInput().getSessionId();
        for (AssistantMessage.ToolCall toolCall : extractToolCalls(assistantMessage)) {
            // 获取工具定义以检查是否需要审核
            Optional<ToolRegistry.RegisteredTool> resolved = toolRegistry.resolve(toolCall.name(), null);
            boolean needReview = resolved.map(rt -> rt.definition().getSandboxPolicy() != null &&
                    rt.definition().getSandboxPolicy().isRequireConfirmation()).orElse(false);
            if (needReview) {
                String decision = null;
                try {
                    ReviewRequest req = new ReviewRequest("tool_execution",
                            "即将执行工具: " + toolCall.name(),
                            toolCall.arguments(),
                            List.of("approve", "reject"), 600);
                    decision = reviewHandler.requestReview(sessionId, req).get(600, TimeUnit.SECONDS);
                } catch (Exception e) {
                    decision = "timeout";
                }
                if (!"approve".equals(decision)) {
                    toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(),
                            "用户拒绝执行或审核超时"));
                    continue;
                }
            }
            // 正常执行工具
            String toolResult = toolExecutor.execute(toolCall);
            toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), toolResult));
            pushAndSaveStep(sessionId, agentId, ReActStepEvent.builder()
                    .type("TOOL_RESULT")
                    .stepNumber(currentStep)
                    .description("工具返回: " + toolCall.name())
                    .executionId(ctx.getExecutionId())
                    .detail(toolResult)
                    .status("SUCCESS")
                    .timestamp(System.currentTimeMillis())
                    .build());
        }
        return toolResponses;
    }





    // ==================== 其他辅助方法 ====================
    private String buildSystemPrompt(ExecutionContext ctx, AgentConfig agentConfig) {
        if (agentConfig != null && agentConfig.getSystemPrompt() != null && !agentConfig.getSystemPrompt().isBlank()) {
            return agentConfig.getSystemPrompt();
        }
        TaskContext taskCtx = ctx.toTaskContext();
        taskCtx.setTaskType("react_conversation");
        PromptContext context = promptPipeline.collect(taskCtx);
        context.setAvailableTools(toolExecutor.getAvailableToolNames());
        context.setToolDescriptions(toolExecutor.getToolDescriptions());
        String prompt = promptPipeline.render(context);
        return promptPipeline.compress(prompt);
    }

    private String generatePlanIfNeeded(ExecutionContext ctx, AgentConfig agentConfig) {
        if (planningStrategy == null) return "";
        String taskType = ctx.toTaskContext().getTaskType();
        if ("code_generation".equals(taskType) || "project_refactor".equals(taskType)) {
            try {
                Map<String, Object> planCtx = Map.of(
                        "projectPath", ctx.getAttribute("projectPath", String.class),
                        "userInput", ctx.getUserInput().getText()
                );
                List<TaskPlan.Step> steps = planningStrategy.generatePlan(ctx.getUserInput().getText(), planCtx);
                return formatPlan(steps);
            } catch (Exception e) {
                log.warn("任务规划生成失败: {}", e.getMessage());
                return "";
            }
        }
        return "";
    }

    private String formatPlan(List<TaskPlan.Step> steps) {
        if (steps == null || steps.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("【执行计划】\n");
        for (int i = 0; i < steps.size(); i++) {
            TaskPlan.Step step = steps.get(i);
            sb.append(String.format("%d. %s (使用工具: %s)\n", i + 1, step.getDescription(), step.getTool()));
        }
        sb.append("请按照上述计划依次调用工具完成任务。\n");
        return sb.toString();
    }

    private ToolCallback[] resolveToolCallbacks(AgentConfig agentConfig) {
        if (!properties.isToolUseEnabled()) return new ToolCallback[0];
        List<ToolCallback> allTools = toolExecutor.getAvailableTools();
        if (agentConfig == null || agentConfig.getTools() == null || agentConfig.getTools().isEmpty()) {
            return deduplicate(allTools);
        }
        Set<String> allowed = new HashSet<>(agentConfig.getTools());
        List<ToolCallback> filtered = allTools.stream()
                .filter(tc -> allowed.contains(tc.getName()))
                .collect(Collectors.toList());
        return deduplicate(filtered);
    }

    private ToolCallback[] getToolCallbacks() {
        if (!properties.isToolUseEnabled()) return new ToolCallback[0];
        return deduplicate(toolExecutor.getAvailableTools());
    }

    private ToolCallback[] deduplicate(List<ToolCallback> tools) {
        Map<String, ToolCallback> unique = new LinkedHashMap<>();
        tools.forEach(tc -> unique.putIfAbsent(tc.getName(), tc));
        return unique.values().toArray(new ToolCallback[0]);
    }

    private void storeConversationMemoryAsync(ExecutionContext ctx, String finalAnswer, String memoryDomain) {
        CompletableFuture.runAsync(() -> {
            try {
                if (finalAnswer == null || finalAnswer.isBlank()) return;
                String userId = ctx.getUserId() != null && !ctx.getUserId().isBlank() ? ctx.getUserId() : "default-user";
                MemoryEntry entry = MemoryEntry.builder()
                        .userId(userId)
                        .content("用户: " + ctx.getUserInput().getText() + "\n助手: " + finalAnswer)
                        .timestamp(Instant.now())
                        .memoryType("EPISODIC")
                        .importance(0.6f)
                        .domain(memoryDomain)
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

    private String cleanStreamText(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("call:\\w+\\{[^}]*\\}", "")
                .replaceAll("<tool_call\\|>|<\\|tool_response>", "");
    }

    private String cleanModelOutput(String text) {
        if (text == null) return "";
        // 只移除工具调用相关标记，不要删除普通字符
        return text.replaceAll("call:\\w+\\{[^}]*\\}", "")
                .replaceAll("<tool_call\\|>|<\\|tool_response>", "")
                .trim();
    }

    private ChatResponse callLLMWithRetry(ChatClient chatClient, List<Message> conversation,
                                          ToolCallback[] tools, int step) {
        Exception lastException = null;
        for (int retry = 0; retry <= llmRetryMax; retry++) {
            try {
                return chatClient.prompt()
                        .messages(conversation)
                        .tools(tools)
                        .options(ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build())
                        .call()
                        .chatResponse();
            } catch (ResourceAccessException e) {
                lastException = e;
                if (retry < llmRetryMax) {
                    log.warn("LLM call timeout at step {}, retrying {}/{} after {} ms",
                            step, retry + 1, llmRetryMax, llmRetryDelayMs);
                    try { Thread.sleep(llmRetryDelayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            } catch (Exception e) {
                lastException = e;
                break;
            }
        }
        log.error("LLM call failed at step {} after {} retries", step, llmRetryMax, lastException);
        return null;
    }

    private boolean hasToolCalls(Message message) {
        return message instanceof AssistantMessage am && am.hasToolCalls();
    }

    private List<AssistantMessage.ToolCall> extractToolCalls(Message message) {
        return message instanceof AssistantMessage am ? am.getToolCalls() : List.of();
    }

    // ---------- 日志方法 ----------
    private void logReActStep(int step, List<Message> conversation) {
        if (!verboseLogging) return;
        log.debug("ReAct step {}/{}", step, properties.getMaxSteps());
        log.info("=== ReAct 第 {} 轮对话历史 ({} 条消息) ===", step, conversation.size());
        for (int i = 0; i < conversation.size(); i++) {
            Message msg = conversation.get(i);
            String role = msg.getMessageType().name();
            String content = msg.getText();
            if (content != null && content.length() > 200) content = content.substring(0, 200) + "...";
            log.info("  [{}] {}: {}", i, role, content);
        }
    }

    private void logLLMResponse(Message assistantMessage) {
        if (!verboseLogging) return;
        if (hasToolCalls(assistantMessage)) {
            List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(assistantMessage);
            log.info("=== LLM 请求调用工具 ({} 个) ===", toolCalls.size());
            toolCalls.forEach(tc -> log.info("  工具名: {}\n  参数: {}", tc.name(), tc.arguments()));
        } else {
            String text = assistantMessage.getText();
            String preview = text.length() > 300 ? text.substring(0, 300) + "..." : text;
            log.info("=== LLM 直接回复 (无工具调用) ===\n{}", preview);
        }
    }

    private void logToolResponses(List<ToolResponseMessage.ToolResponse> toolResponses) {
        if (!verboseLogging) return;
        log.info("=== 工具执行结果已反馈给 LLM ===");
        toolResponses.forEach(tr -> {
            String preview = tr.responseData().length() > 200 ? tr.responseData().substring(0, 200) + "..." : tr.responseData();
            log.info("  工具 {} 返回: {}", tr.name(), preview);
        });
    }

    private void logCompletion(int step, String finalAnswer, String modelUsed) {
        log.info("=== ReAct 执行完成 ===");
        log.info("  总步数: {}", step);
        log.info("  最终回复: {}", finalAnswer);
        log.info("  模型: {}", modelUsed);
    }
}