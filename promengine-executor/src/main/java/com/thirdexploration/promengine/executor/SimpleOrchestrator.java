package com.thirdexploration.promengine.executor;

import com.thirdexploration.promengine.core.domain.*;
import com.thirdexploration.promengine.core.ModelGateway;
import com.thirdexploration.promengine.executor.execution.ExecutionContext;
import com.thirdexploration.promengine.executor.tool.registry.ToolRegistry;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.prompt.core.PromptContext;
import com.thirdexploration.promengine.prompt.core.PromptPipeline;
import com.thirdexploration.promengine.prompt.util.PromptLoggingUtils;
import com.thirdexploration.promengine.skill.SkillExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Slf4j
@Component
@ConditionalOnProperty(name = "promengine.orchestrator.mode", havingValue = "SIMPLE", matchIfMissing = true)
@RequiredArgsConstructor
public class SimpleOrchestrator implements Orchestrator {

    private final ModelGateway modelGateway;
    private final UnifiedMemoryAPI memoryAPI;
    private final ToolRegistry toolRegistry;
    private final SkillExecutor skillExecutor;
    private final PromptPipeline promptPipeline;      // 统一管线

    @Override
    public CompletableFuture<Response> execute(ExecutionContext ctx) {
        // 1. 通过管线收集上下文并渲染 Prompt
        TaskContext taskCtx = ctx.toTaskContext();
        PromptContext context = promptPipeline.collect(taskCtx);
        String prompt = promptPipeline.render(context);
        prompt = promptPipeline.compress(prompt);

        // 2. 打印日志（可选）
        PromptLoggingUtils.debugPrompt("系统提示词", context.getMemories(), ctx.getUserInput().getText(), prompt);

        // 3. 调用模型
        CompletionRequest request = CompletionRequest.builder()
                .modelId("default")
                .prompt(prompt)
                .maxTokens(2000)
                .temperature(0.7f)
                .build();
        CompletionResult result = modelGateway.complete(request);

        // 4. 存储对话记忆
        storeConversationMemory(ctx, result.getContent());

        return CompletableFuture.completedFuture(
                Response.builder()
                        .text(result.getContent())
                        .processingTimeMs(result.getLatencyMs())
                        .modelUsed(request.getModelId())
                        .cost(0.0)
                        .build()
        );
    }

    @Override
    public Stream<CompletionChunk> executeStream(ExecutionContext ctx) {
        // 1. 通过管线收集上下文并渲染 Prompt
        TaskContext taskCtx = ctx.toTaskContext();
        PromptContext context = promptPipeline.collect(taskCtx);
        String prompt = promptPipeline.render(context);
        prompt = promptPipeline.compress(prompt);

        // 2. 构建模型请求
        CompletionRequest request = CompletionRequest.builder()
                .modelId("default")
                .prompt(prompt)
                .maxTokens(2000)
                .temperature(0.7f)
                .includeThinking(true)
                .taskContext(null)
                .build();

        // 3. 获取流式响应
        Stream<CompletionChunk> chunkStream = modelGateway.stream(request);

        // 4. 收集完整回复并异步存储
        StringBuilder fullContent = new StringBuilder();
        return chunkStream.peek(chunk -> {
            if (!chunk.isLast()) {
                fullContent.append(chunk.getDelta());
            } else {
                CompletableFuture.runAsync(() -> {
                    storeConversationMemory(ctx, fullContent.toString());
                    log.debug("Stream completed, memory stored. Total length: {}", fullContent.length());
                });
            }
        }).onClose(() -> log.debug("Chunk stream closed"));
    }

    /**
     * 存储对话记忆（统一逻辑）
     */
    private void storeConversationMemory(ExecutionContext ctx, String finalAnswer) {
        MemoryEntry entry = MemoryEntry.builder()
                .userId(ctx.getUserId())
                .content("用户: " + ctx.getUserInput().getText() + "\n助手: " + finalAnswer)
                .summary(finalAnswer.length() > 200 ? finalAnswer.substring(0, 200) : finalAnswer)
                .timestamp(Instant.now())
                .memoryType("EPISODIC")
                .importance(0.5f)
                .domain("general")
                .layer("episodic")
                .strength(1.0f)
                .sharingLevel("private")
                .metadata(Map.of("sessionId", ctx.getUserInput().getSessionId()))
                .build();
        memoryAPI.remember(entry);
    }
}