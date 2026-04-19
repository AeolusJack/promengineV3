package com.thirdexploration.promengine.swarm;

import com.thirdexploration.promengine.core.domain.TaskContext;
import com.thirdexploration.promengine.swarm.executor.MicroAgentExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class SwarmCoordinator {

    private final MicroAgentFactory agentFactory;
    private final MicroAgentExecutor executor;
    private final Blackboard blackboard;
    private final SwarmProperties properties;
    private final AtomicInteger activeAgents = new AtomicInteger(0);
    private final ExecutorService swarmPool = Executors.newVirtualThreadPerTaskExecutor();

    public List<MicroAgentResult> orchestrate(TaskContext ctx, List<String> subtasks) {
        if (activeAgents.get() >= properties.getMaxConcurrentAgents()) {
            log.warn("Max concurrent agents reached, queuing tasks");
        }

        List<CompletableFuture<MicroAgentResult>> futures = new ArrayList<>();
        for (String subtask : subtasks) {
            MicroAgent agent = agentFactory.createForSubtask(subtask);
            CompletableFuture<MicroAgentResult> future = CompletableFuture.supplyAsync(() -> {
                activeAgents.incrementAndGet();
                try {
                    return executor.execute(agent, ctx);
                } finally {
                    activeAgents.decrementAndGet();
                }
            }, swarmPool).orTimeout(properties.getAgentTimeout().toMillis(), TimeUnit.MILLISECONDS)
              .exceptionally(ex -> MicroAgentResult.failure(agent.getId(), ex.getMessage()));
            futures.add(future);
        }

        // 等待所有完成并汇总
        List<MicroAgentResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // 写入黑板
        results.forEach(r -> blackboard.put(r.getAgentId(), r.getOutput()));

        return results;
    }

    public String summarize(List<MicroAgentResult> results) {
        // 调用大模型汇总结果，此处简化
        return "Swarm summary: " + results.size() + " agents completed";
    }

    public void cleanup() {
        blackboard.clear();
    }
}