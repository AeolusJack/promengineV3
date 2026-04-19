package com.thirdexploration.promengine.model.gateway;

import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.CompletionRequest;
import com.thirdexploration.promengine.core.domain.CompletionResult;

import java.util.stream.Stream;

/**
 * 模型适配器接口，定义了统一的模型调用规范。
 * 所有具体模型提供商（如 LiteLLM、Ollama、OpenRouter 等）都需要实现此接口。
 *
 * @author Third Exploration
 * @version 1.0
 */
public interface ModelAdapter {

    /**
     * 获取当前适配器对应的提供商标识。
     *
     * @return 提供商标识，如 "litellm", "ollama", "siliconflow" 等
     */
    String getProviderId();

    /**
     * 同步执行模型完成请求。
     *
     * @param request 包含模型ID、提示词、参数等信息的请求对象
     * @return 模型生成的结果，包含内容、Token 用量等
     * @throws com.thirdexploration.promengine.core.exception.ModelUnavailableException 如果模型不可用
     */
    CompletionResult complete(CompletionRequest request);

    /**
     * 流式执行模型完成请求。
     *
     * @param request 包含模型ID、提示词、参数等信息的请求对象
     * @return 一个按顺序产生 CompletionChunk 的 Stream，支持流式输出
     */
    Stream<CompletionChunk> stream(CompletionRequest request);

    /**
     * 检查当前适配器对应的模型提供商是否可用。
     * 可用于健康检查和熔断判断。
     *
     * @return true 表示服务可用，false 表示不可用
     */
    boolean isAvailable();

    /**
     * 获取当前适配器支持的最大上下文长度（Token 数）。
     * 默认返回一个较大的保守值，子类可重写以提供精确限制。
     *
     * @param modelName 模型名称
     * @return 最大 Token 数
     */
    default long getMaxContextLength(String modelName) {
        return 8192L; // 默认 8K
    }

    /**
     * 获取当前适配器支持的功能特性。
     * 可用于路由决策。
     *
     * @param modelName 模型名称
     * @return 功能特性位掩码或描述
     */
    default ModelCapabilities getCapabilities(String modelName) {
        return ModelCapabilities.builder()
                .supportsStreaming(true)
                .supportsFunctionCalling(false)
                .supportsVision(false)
                .build();
    }

    /**
     * 模型功能特性描述。
     */
    @lombok.Builder
    record ModelCapabilities(
            boolean supportsStreaming,
            boolean supportsFunctionCalling,
            boolean supportsVision
    ) {}
}