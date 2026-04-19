package com.thirdexploration.promengine;

import com.thirdexploration.promengine.core.AgentRuntime;
import com.thirdexploration.promengine.core.domain.AgentState;
import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.Response;
import com.thirdexploration.promengine.core.domain.UserInput;
import com.thirdexploration.promengine.executor.Orchestrator;
import com.thirdexploration.promengine.executor.execution.ExecutionContext;
import com.thirdexploration.promengine.executor.execution.TaskQueue;
import com.thirdexploration.promengine.model.gateway.DefaultModelGateway;
import com.thirdexploration.promengine.memory.service.DefaultMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntimeImpl implements AgentRuntime {

    private final DefaultModelGateway modelGateway;
    private final DefaultMemoryService memoryService;
    private final Orchestrator orchestrator;
    private final TaskQueue taskQueue;
    private volatile boolean running = false;

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
                .activeMicroAgents(0)
                .memoryEntriesTotal(memoryService.count())
                .build();
    }

    @Override
    public CompletableFuture<Response> process(UserInput input) {
        ExecutionContext ctx = ExecutionContext.of(input);
        return orchestrator.execute(ctx);
    }

    @Override
    public Stream<CompletionChunk> processStream(UserInput input) {
        ExecutionContext ctx =  ExecutionContext.of(input);
        return orchestrator.executeStream(ctx);
    }
}