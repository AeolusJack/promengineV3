package com.thirdexploration.promengine.runtime;

import com.thirdexploration.promengine.core.AgentRuntime;
import com.thirdexploration.promengine.core.agent.TaskPlan;
import com.thirdexploration.promengine.core.agent.TaskPlanningStrategy;
import com.thirdexploration.promengine.core.domain.AgentState;
import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.Response;
import com.thirdexploration.promengine.core.domain.UserInput;
import com.thirdexploration.promengine.core.trace.TraceContext;
import com.thirdexploration.promengine.devtools.DevToolsProperties;
import com.thirdexploration.promengine.executor.Orchestrator;
import com.thirdexploration.promengine.executor.execution.ExecutionContext;
import com.thirdexploration.promengine.executor.execution.TaskQueue;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import com.thirdexploration.promengine.model.gateway.DefaultModelGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * AgentRuntimeImpl 是 PromEngine 的核心运行时实现，实现了 AgentRuntime 接口，负责：
 *
 * 生命周期管理：start() 初始化各子系统，shutdown() 优雅关闭。
 *
 * 状态查询：getState() 返回当前运行状态（模式、活跃微 Agent 数、记忆总数等），供监控和前端展示。
 *
 * 请求处理：process() 接收用户输入，委托给 Orchestrator 执行并返回响应。
 *
 * 它是连接外部 API（如 ChatController）与内部编排器的桥梁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntimeImpl implements AgentRuntime {

    private final DefaultModelGateway modelGateway;
    private final UnifiedMemoryAPI memoryAPI;
    private final Orchestrator orchestrator;  // 改为接口
    private final TaskQueue taskQueue;
    private volatile boolean running = false;
    private final DevToolsProperties devToolsProperties;
    @Autowired(required = false)
    private TaskPlanningStrategy shadowPlanningStrategy;
    @Override
    public void start() {
        running = true;
        log.info("PromEngine Runtime started");
        taskQueue.start();
    }

    @Override
    public void shutdown() {
        running = false;
        taskQueue.shutdown();
        log.info("PromEngine Runtime shutdown");
    }

    @Override
    public AgentState getState() {
        return AgentState.builder()
                .running(running)
                .mode("carbon")
                .activeMicroAgents(taskQueue.getActiveTaskCount())
                .memoryEntriesTotal(memoryAPI.count())
                .build();
    }



    @Override
    public CompletableFuture<Response> process(UserInput input) {
        ExecutionContext ctx = ExecutionContext.of(input);
        CompletableFuture<Response> mainFuture = orchestrator.execute(ctx);

        // 影子模式：仅当 devtools 启用且影子规划器存在时异步执行
        if (devToolsProperties.isShadowModeEnabled() && shadowPlanningStrategy != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    // 从请求中提取 agentConfig（如果前端传了）
                    Map<String, Object> shadowCtx = new HashMap<>();
                    Object agentConfigObj = input.getMetadata().get("agentConfig");
                    if (agentConfigObj instanceof Map) {
                        shadowCtx.put("agentConfig", agentConfigObj);
                    }
                    List<TaskPlan.Step> shadowPlan = shadowPlanningStrategy.generatePlan(input.getText(), shadowCtx);
                    log.info("[SHADOW] Shadow plan generated with {} steps", shadowPlan.size());
                } catch (Exception e) {
                    log.error("[SHADOW] Shadow execution failed", e);
                }
            });
        }
        return mainFuture;
    }

    @Override
    public Stream<CompletionChunk> processStream(UserInput input) {
        String traceId = TraceContext.generateTraceId();
        TraceContext.setTraceId(traceId);
        ExecutionContext ctx = ExecutionContext.of(input);
        ctx.putAttribute("traceId", traceId);
        // Stream 结束后清除（使用 onClose）
        return orchestrator.executeStream(ctx).onClose(TraceContext::clear);
    }
}