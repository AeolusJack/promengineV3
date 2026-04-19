package com.thirdexploration.promengine.executor;

import com.thirdexploration.promengine.core.domain.*;
import com.thirdexploration.promengine.core.MemoryService;
import com.thirdexploration.promengine.core.ModelGateway;
import com.thirdexploration.promengine.executor.execution.ExecutionContext;

import com.thirdexploration.promengine.skill.SkillExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class Orchestrator {

    private final ModelGateway modelGateway;
    private final MemoryService memoryService;
    private final ToolRegistry toolRegistry;
    private final SkillExecutor skillExecutor;

    public CompletableFuture<Response> execute(ExecutionContext ctx) {
        // 1. 检索相关记忆
        Query query = Query.builder().text(ctx.getUserInput().getText()).userId(ctx.getUserId()).build();
        RetrievalStrategy strategy = RetrievalStrategy.builder()
                .timeWindow(java.time.Duration.ofDays(30))
                .allowColdStorageScan(false)
                .topK(5)
                .build();
        SearchResult memoryResult = memoryService.retrieve(query, strategy);

        // 2. 构建提示词
        String prompt = buildPrompt(ctx, memoryResult);

        // 3. 调用模型
        CompletionRequest request = CompletionRequest.builder()
                .modelId("default")
                .prompt(prompt)
                .maxTokens(2000)
                .temperature(0.7f)
                .build();
      CompletionResult result = modelGateway.complete(request);
      //  CompletionResult result = ollamaAdapter.complete(request);  //临时测试用
        // 4. 存储本次交互为记忆
        MemoryEntry entry = MemoryEntry.builder()
                .userId(ctx.getUserId())
                .content("用户: " + ctx.getUserInput().getText() + "\n助手: " + result.getContent())
                .timestamp(Instant.now())
                .type(MemoryEntry.MemoryType.EPISODIC)
                .importance(0.5f)
                .build();
        memoryService.store(entry);

        Response response = Response.builder()
                .text(result.getContent())
                .processingTimeMs(result.getLatencyMs())
                .modelUsed(request.getModelId())
                .cost(0.0)
                .build();

        return CompletableFuture.completedFuture(response);
    }
    public Stream<CompletionChunk> executeStream(ExecutionContext ctx) {
        // 同步检索记忆（若耗时较长可考虑异步预热，但为简单直接同步）
        Query query = Query.builder().text(ctx.getUserInput().getText()).userId(ctx.getUserId()).build();
        RetrievalStrategy strategy = RetrievalStrategy.builder()
                .timeWindow(Duration.ofDays(30))
                .topK(5)
                .build();
        SearchResult memoryResult = memoryService.retrieve(query, strategy);

        String prompt = buildPrompt(ctx, memoryResult);

        CompletionRequest request = CompletionRequest.builder()
                .modelId("default")
                .prompt(prompt)
                .maxTokens(2000)
                .temperature(0.7f)
                .includeThinking(true)
                .taskContext(null) //任务上下文暂无 todo 后续补上
                .build();

        // 获取流
        Stream<CompletionChunk> chunkStream = modelGateway.stream(request);

        // 收集完整回复以便存储
        StringBuilder fullContent = new StringBuilder();
        return chunkStream.peek(chunk -> {
            if (!chunk.isLast()) {
                fullContent.append(chunk.getDelta());
            } else {
                // 流结束，异步存储记忆
                CompletableFuture.runAsync(() -> {
                    MemoryEntry entry = MemoryEntry.builder()
                            .userId(ctx.getUserId())
                            .content("用户: " + ctx.getUserInput().getText() + "\n助手: " + fullContent)
                            .timestamp(Instant.now())
                            .type(MemoryEntry.MemoryType.EPISODIC)
                            .importance(0.5f)
                            .metadata(Map.of("sessionId", ctx.getUserInput().getSessionId()))
                            .build();
                    memoryService.store(entry);
                    log.debug("Stream completed, memory stored. Total length: {}", fullContent.length());
                });
            }
        }).onClose(() -> log.debug("Chunk stream closed"));
    }
    private String buildPrompt(ExecutionContext ctx, SearchResult memories) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个有记忆的智能助手。\n");
        sb.append("相关记忆：\n");
        for (SearchResult.MemoryHit hit : memories.getHits()) {
            sb.append("- ").append(hit.getContent()).append("\n");
        }
        sb.append("用户: ").append(ctx.getUserInput().getText());
        return sb.toString();
    }
}