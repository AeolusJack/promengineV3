package com.thirdexploration.promengine.core;

import com.thirdexploration.promengine.core.domain.AgentState;
import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.Response;
import com.thirdexploration.promengine.core.domain.UserInput;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 智能体运行时顶层接口。
 * 负责管理整个 Agent 的生命周期和处理用户输入。
 *
 * @author Third Exploration
 * @version 1.0
 */
public interface AgentRuntime {

    /**
     * 启动运行时，初始化所有子系统。
     */
    void start();

    /**
     * 优雅关闭运行时，释放资源。
     */
    void shutdown();

    /**
     * 获取当前 Agent 的运行状态快照。
     *
     * @return AgentState 包含模式、认知状态等
     */
    AgentState getState();

    /**
     * 处理用户输入（异步）。
     *
     * @param input 用户输入封装
     * @return 包含响应的 Future
     */
    CompletableFuture<Response> process(UserInput input);

    // 新增：流式处理，返回 CompletionChunk 流
    Stream<CompletionChunk> processStream(UserInput input);
}