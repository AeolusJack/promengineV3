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
    private final PromptPipeline promptPipeline;

    @Override
    public CompletableFuture<Response> execute(ExecutionContext ctx) {
        // 检查 Agent 专属配置
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

        // 构建提示词：如果有覆盖，则直接使用 Agent 系统提示词 + 用户消息；否则走管线
        String prompt;
        if (systemPromptOverride != null && !systemPromptOverride.isBlank()) {
            // 使用 Agent 专属系统提示词，用户输入直接拼接
            prompt = systemPromptOverride + "\n\n用户: " + ctx.getUserInput().getText() + "\n助手:";
        } else {
            TaskContext taskCtx = ctx.toTaskContext();
            PromptContext context = promptPipeline.collect(taskCtx);
            prompt = promptPipeline.render(context);
            prompt = promptPipeline.compress(prompt);
        }

        // 调用模型，可能根据 Agent 配置选择模型
        CompletionRequest request = CompletionRequest.builder()
                .modelId(agentConfig != null && agentConfig.get("modelPreference") != null ?
                        (String) agentConfig.get("modelPreference") : "default")
                .prompt(prompt)
                .maxTokens(2000)
                .temperature(0.7f)
                .build();
        CompletionResult result = modelGateway.complete(request);

        // 存储记忆，使用 Agent 的记忆域
        storeConversationMemory(ctx, result.getContent(), memoryDomain);

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
        @SuppressWarnings("unchecked")
        Map<String, Object> agentConfig = (Map<String, Object>) ctx.getAttribute("agentConfig", Map.class);
        String systemPromptOverride = agentConfig != null ? (String) agentConfig.get("systemPrompt") : null;
        String memoryDomain = agentConfig != null ? (String) agentConfig.getOrDefault("memoryDomain", "general") : "general";

        String prompt;
        if (systemPromptOverride != null && !systemPromptOverride.isBlank()) {
            prompt = systemPromptOverride + "\n\n用户: " + ctx.getUserInput().getText() + "\n助手:";
        } else {
            TaskContext taskCtx = ctx.toTaskContext();
            PromptContext context = promptPipeline.collect(taskCtx);
            prompt = promptPipeline.render(context);
            prompt = promptPipeline.compress(prompt);
        }

        CompletionRequest request = CompletionRequest.builder()
                .modelId(agentConfig != null && agentConfig.get("modelPreference") != null ?
                        (String) agentConfig.get("modelPreference") : "default")
                .prompt(prompt)
                .maxTokens(2000)
                .temperature(0.7f)
                .includeThinking(true)
                .build();

        Stream<CompletionChunk> chunkStream = modelGateway.stream(request);
        StringBuilder fullContent = new StringBuilder();
        return chunkStream.peek(chunk -> {
            if (!chunk.isLast()) {
                fullContent.append(chunk.getDelta());
            } else {
                CompletableFuture.runAsync(() -> {
                    storeConversationMemory(ctx, fullContent.toString(), memoryDomain);
                });
            }
        }).onClose(() -> log.debug("Chunk stream closed"));
    }

    private void storeConversationMemory(ExecutionContext ctx, String finalAnswer, String domain) {
        MemoryEntry entry = MemoryEntry.builder()
                .userId(ctx.getUserId())
                .content("用户: " + ctx.getUserInput().getText() + "\n助手: " + finalAnswer)
                .summary(finalAnswer.length() > 200 ? finalAnswer.substring(0, 200) : finalAnswer)
                .timestamp(Instant.now())
                .memoryType("EPISODIC")
                .importance(0.5f)
                .domain(domain != null ? domain : "general")   // 使用 Agent 指定域
                .layer("episodic")
                .strength(1.0f)
                .sharingLevel("private")
                .metadata(Map.of("sessionId", ctx.getUserInput().getSessionId()))
                .build();
        memoryAPI.remember(entry);
    }
}