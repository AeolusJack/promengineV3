package com.thirdexploration.promengine.core;

import com.thirdexploration.promengine.core.domain.CompletionRequest;
import com.thirdexploration.promengine.core.domain.CompletionResult;
import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.CostEstimate;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 模型网关，统一不同模型提供商的调用。
 */
public interface ModelGateway {

    /**
     * 同步完成请求。
     */
    CompletionResult complete(CompletionRequest request);

    /**
     * 流式完成请求。
     */
    Stream<CompletionChunk> stream(CompletionRequest request);

    /**
     * 检查指定模型是否可用。
     */
    boolean isAvailable(String modelId);

    /**
     * 设置降级链。
     */
    void addFallbackChain(List<String> modelIds);

    /**
     * 预估请求成本。
     */
    CostEstimate estimateCost(CompletionRequest request);

    /**
     * 获取各模型提供者的负载信息。
     */
    Map<String, ModelLoadInfo> getLoadInfo();

    interface ModelLoadInfo {
        int getQueueLength();
        double getAverageLatency();
        boolean isOverloaded();
    }
}