package com.thirdexploration.promengine.executor;

import com.thirdexploration.promengine.core.MemoryService;
import com.thirdexploration.promengine.core.PromptManager;
import com.thirdexploration.promengine.core.domain.*;
import com.thirdexploration.promengine.executor.config.OrchestratorProperties;
import com.thirdexploration.promengine.executor.execution.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

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
    private final MemoryService memoryService;
    private final PromptManager promptManager;
    private final OrchestratorProperties properties;
    @Value("${promengine.orchestrator.verbose-logging:false}")
    private boolean verboseLogging;
    @Override
    public CompletableFuture<Response> execute(ExecutionContext ctx) {
        log.info("ReactOrchestrator (M7) started for session: {}", ctx.getUserInput().getSessionId());
        long startTime = System.currentTimeMillis();

        // 1. 检索记忆
        Query query = Query.builder()
                .text(ctx.getUserInput().getText())
                .userId(ctx.getUserId())
                .maxResults(5)
                .build();
        RetrievalStrategy strategy = RetrievalStrategy.builder()
                .timeWindow(Duration.ofDays(30))
                .allowColdStorageScan(false)
                .topK(5)
                .build();
        SearchResult memoryResult = memoryService.retrieve(query, strategy);

        // 2. 构建初始对话
        List<Message> conversation = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(ctx, memoryResult);
        conversation.add(new SystemMessage(systemPrompt));
        conversation.add(new UserMessage(ctx.getUserInput().getText()));

        // 3. 获取 ChatClient 并注册工具
        ChatClient chatClient = chatClientBuilder
                .defaultTools(getToolCallbacks())
                .build();

        int step = 0;
        String finalAnswer = null;
        String modelUsed = null;

        while (step < properties.getMaxSteps()) {
            step++;

            //观测日志开始
            log.debug("ReAct step {}/{}", step, properties.getMaxSteps());
            // ===== 打印当前对话历史 =====
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
            //观测日志结束


            ChatResponse response;
            try {
                response = chatClient.prompt()
                        .messages(conversation)
                        .call()
                        .chatResponse();
                if (modelUsed == null && response.getMetadata() != null) {
                    modelUsed = response.getMetadata().getModel();
                }
            } catch (Exception e) {
                log.error("LLM call failed at step {}", step, e);
                finalAnswer = "抱歉，模型调用出现异常，请稍后重试。";
                break;
            }

            // 4. 提取助手消息
            Message assistantMessage = response.getResult().getOutput();
            conversation.add(assistantMessage);
            //观测日志开始
             // ===== 打印 LLM 响应 =====
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
            //观测日志结束


            // 5. 检查是否有工具调用
            if (hasToolCalls(assistantMessage) && properties.isToolUseEnabled()) {
                // 执行每个工具调用，并将结果作为 ToolResponseMessage 加入对话
                List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
                for (AssistantMessage.ToolCall toolCall : extractToolCalls(assistantMessage)) {
                    log.info("Executing tool: {} with args: {}", toolCall.name(), toolCall.arguments());
                    String toolResult = toolExecutor.execute(toolCall);
                    toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), toolResult));
                }
                conversation.add(new ToolResponseMessage(toolResponses));
                //观测日志开始
                // ===== 打印工具反馈 =====
                log.info("=== 工具执行结果已反馈给 LLM ===");
                for (ToolResponseMessage.ToolResponse tr : toolResponses) {
                    String preview = tr.responseData().length() > 200 ? tr.responseData().substring(0, 200) + "..." : tr.responseData();
                    log.info("  工具 {} 返回: {}", tr.name(), preview);
                }
                //观测日志结束
                // 继续循环，让模型处理工具结果
            } else {
                // 无工具调用，获取最终回复
                finalAnswer = assistantMessage.getText();
                break;
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "任务执行超过最大步数限制，已终止。";
        }
        log.info("=== ReAct 执行完成 ===");
        log.info("  总步数: {}", step);
        log.info("  最终回复: {}", finalAnswer);
        log.info("  模型: {}", modelUsed);
        // 6. 存储记忆
        storeConversationMemory(ctx, finalAnswer);

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
        log.info("--------=====----暂时未实现");
        //todo 待实现该模式下stream
        return Stream.empty();
    }

    // ------------------- 私有辅助方法 -------------------

    private String buildSystemPrompt(ExecutionContext ctx, SearchResult memories) {
        TaskContext taskCtx = TaskContext.builder()
                .userId(ctx.getUserId())
                .userInput(ctx.getUserInput())
                .taskType("react_conversation")
                .variables(Map.of(
                        "available_tools", toolExecutor.getToolDescriptions(),
                        "memories", memories.getHits()
                ))
                .build();
        return promptManager.render(taskCtx).getFinalPrompt();
    }

    /**
     * 获取所有工具回调（适配 M7 的 ToolCallback）。
     */
    private ToolCallback[] getToolCallbacks() {
        if (!properties.isToolUseEnabled()) {
            return new ToolCallback[0];
        }
        ToolCallback[] callbacks = toolExecutor.getAvailableTools().toArray(new ToolCallback[0]);
        log.info("=== 可用工具列表 ({} 个) ===", callbacks.length);
        for (ToolCallback cb : callbacks) {
            log.info("  - {}: {}", cb.getToolDefinition().name(), cb.getToolDefinition().description());
        }
        return callbacks;
    }

    /**
     * 判断助手消息是否包含工具调用。
     */
    private boolean hasToolCalls(Message message) {
        if (message instanceof AssistantMessage assistantMessage) {
            return assistantMessage.hasToolCalls();
        }
        return false;
    }

    /**
     * 从助手消息中提取工具调用列表。
     */
    private List<AssistantMessage.ToolCall> extractToolCalls(Message message) {
        if (message instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getToolCalls();
        }
        return List.of();
    }

    /**
     * 存储对话记忆。
     */
    private void storeConversationMemory(ExecutionContext ctx, String finalAnswer) {
        try {
            MemoryEntry entry = MemoryEntry.builder()
                    .userId(ctx.getUserId())
                    .content("用户: " + ctx.getUserInput().getText() + "\n助手: " + finalAnswer)
                    .timestamp(Instant.now())
                    .type(MemoryEntry.MemoryType.EPISODIC)
                    .importance(0.6f)
                    .build();
            memoryService.store(entry);
        } catch (Exception e) {
            log.warn("Failed to store conversation memory", e);
        }
    }
}