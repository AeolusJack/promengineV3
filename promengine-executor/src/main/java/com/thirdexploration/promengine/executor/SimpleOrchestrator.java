package com.thirdexploration.promengine.executor;

import com.thirdexploration.promengine.core.AgentConfig;
import com.thirdexploration.promengine.core.ModelGateway;
import com.thirdexploration.promengine.core.agent.AgentConfigProvider;
import com.thirdexploration.promengine.core.agent.ContextProvider;
import com.thirdexploration.promengine.core.domain.*;
import com.thirdexploration.promengine.executor.execution.ExecutionContext;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.prompt.core.PromptContext;
import com.thirdexploration.promengine.prompt.core.PromptPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@ConditionalOnProperty(name = "promengine.orchestrator.mode", havingValue = "SIMPLE", matchIfMissing = true)
public class SimpleOrchestrator implements Orchestrator {

    private final ModelGateway modelGateway;
    private final UnifiedMemoryAPI memoryAPI;
    private final PromptPipeline promptPipeline;
    private final AgentConfigProvider agentConfigProvider;
    // 所有可用的上下文提供者，可能为空
    private final List<ContextProvider> contextProviders;

    // 注意：移除了 ToolRegistry 和 SkillExecutor，Simple 模式不直接使用
    // 如果未来需要工具列表展示，可重新引入

    public SimpleOrchestrator(ModelGateway modelGateway,
                              UnifiedMemoryAPI memoryAPI,
                              PromptPipeline promptPipeline,
                              AgentConfigProvider agentConfigProvider,
                              List<ContextProvider> contextProviders) {
        this.modelGateway = modelGateway;
        this.memoryAPI = memoryAPI;
        this.promptPipeline = promptPipeline;
        this.agentConfigProvider = agentConfigProvider;
        this.contextProviders = contextProviders != null ? contextProviders : List.of();
    }

    @Override
    public CompletableFuture<Response> execute(ExecutionContext ctx) {
        // 1. 加载 Agent 配置（若有）
        String agentId = ctx.getAttribute("agentId", String.class);
        AgentConfig agentConfig = agentId != null ? agentConfigProvider.getConfig(agentId) : null;

        // 2. 收集上下文
        Map<String, Object> extraContext = collectContext(agentConfig, ctx);

        // 3. 构建提示词
        String prompt = buildPrompt(ctx, agentConfig, extraContext);

        // 4. 选择模型
        String modelId = agentConfig != null && agentConfig.getModelPreference() != null
                ? agentConfig.getModelPreference()
                : "default";

        // 5. 调用模型
        CompletionRequest request = CompletionRequest.builder()
                .modelId(modelId)
                .prompt(prompt)
                .maxTokens(2000)
                .temperature(0.7f)
                .build();
        CompletionResult result = modelGateway.complete(request);

        // 6. 存储记忆
        String memoryDomain = agentConfig != null ? agentConfig.getMemoryDomain() : "general";
        storeConversationMemory(ctx, result.getContent(), memoryDomain);

        return CompletableFuture.completedFuture(
                Response.builder()
                        .text(result.getContent())
                        .processingTimeMs(result.getLatencyMs())
                        .modelUsed(modelId)
                        .cost(0.0)
                        .build()
        );
    }

    @Override
    public Stream<CompletionChunk> executeStream(ExecutionContext ctx) {
        String agentId = ctx.getAttribute("agentId", String.class);
        AgentConfig agentConfig = agentId != null ? agentConfigProvider.getConfig(agentId) : null;

        Map<String, Object> extraContext = collectContext(agentConfig, ctx);
        String prompt = buildPrompt(ctx, agentConfig, extraContext);

        String modelId = agentConfig != null && agentConfig.getModelPreference() != null
                ? agentConfig.getModelPreference()
                : "default";
        String memoryDomain = agentConfig != null ? agentConfig.getMemoryDomain() : "general";

        CompletionRequest request = CompletionRequest.builder()
                .modelId(modelId)
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
                CompletableFuture.runAsync(() ->
                    storeConversationMemory(ctx, fullContent.toString(), memoryDomain));
            }
        }).onClose(() -> log.debug("Chunk stream closed"));
    }

    // ==================== 私有方法 ====================

    /** 收集领域上下文（从 ContextProvider） */
    private Map<String, Object> collectContext(AgentConfig agentConfig, ExecutionContext ctx) {
        if (agentConfig == null || contextProviders.isEmpty()) return Collections.emptyMap();

        String type = agentConfig.getType(); // 需要在 AgentConfig 中添加 type 字段，如 "code", "finance"
        if (type == null) return Collections.emptyMap();

        Map<String, Object> result = new HashMap<>();
        for (ContextProvider provider : contextProviders) {
            if (type.equals(provider.getType())) {
                result.putAll(provider.collect(
                    ctx.getUserId(),
                    ctx.getSessionId(),
                    ctx.getAttribute("projectId", String.class)
                ));
            }
        }
        return result;
    }

    /** 构建最终提示词 */
    private String buildPrompt(ExecutionContext ctx, AgentConfig agentConfig, Map<String, Object> extraContext) {
        // 优先使用 Agent 专属系统提示词
        if (agentConfig != null && agentConfig.getSystemPrompt() != null && !agentConfig.getSystemPrompt().isBlank()) {
            StringBuilder sb = new StringBuilder(agentConfig.getSystemPrompt());
            // 注入可用工具描述（仅限 Agent 允许的工具）
            if (agentConfig.getTools() != null && !agentConfig.getTools().isEmpty()) {
                // 从 ToolRegistry 获取工具描述并过滤，但这里我们没有直接引用 ToolRegistry，
                // 为保持简洁，暂不注入工具描述。如需注入，可引入 ToolRegistry 或通过 AgentConfig 传递。
                // 由于 Simple 模式不执行工具，该描述为可选。
            }
            // 注入额外上下文
            if (!extraContext.isEmpty()) {
                sb.append("\n\n【补充信息】\n");
                extraContext.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
            }
            sb.append("\n\n用户: ").append(ctx.getUserInput().getText()).append("\n助手:");
            return sb.toString();
        }

        // 默认走提示词管线，并注入上下文
        TaskContext taskCtx = ctx.toTaskContext();
        PromptContext context = promptPipeline.collect(taskCtx);
        if (!extraContext.isEmpty()) {
            context.getExtraVariables().putAll(extraContext);
        }
        String prompt = promptPipeline.render(context);
        return promptPipeline.compress(prompt);
    }

    private void storeConversationMemory(ExecutionContext ctx, String finalAnswer, String domain) {
        MemoryEntry entry = MemoryEntry.builder()
                .userId(ctx.getUserId())
                .content("用户: " + ctx.getUserInput().getText() + "\n助手: " + finalAnswer)
                .summary(finalAnswer.length() > 200 ? finalAnswer.substring(0, 200) : finalAnswer)
                .timestamp(Instant.now())
                .memoryType("EPISODIC")
                .importance(0.5f)
                .domain(domain != null ? domain : "general")
                .layer("episodic")
                .strength(1.0f)
                .sharingLevel("private")
                .metadata(Map.of("sessionId", ctx.getUserInput().getSessionId()))
                .build();
        memoryAPI.remember(entry);
    }
}