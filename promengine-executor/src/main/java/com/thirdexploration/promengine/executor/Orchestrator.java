package com.thirdexploration.promengine.executor;

import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.Response;
import com.thirdexploration.promengine.executor.execution.ExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public interface Orchestrator {

    public CompletableFuture<Response> execute(ExecutionContext ctx);

    Stream<CompletionChunk> executeStream(ExecutionContext ctx);
}
