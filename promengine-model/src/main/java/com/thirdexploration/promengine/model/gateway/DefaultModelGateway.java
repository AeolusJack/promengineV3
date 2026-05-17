package com.thirdexploration.promengine.model.gateway;

import com.thirdexploration.promengine.core.ModelGateway;
import com.thirdexploration.promengine.core.cache.SemanticCache;
import com.thirdexploration.promengine.core.domain.*;
import com.thirdexploration.promengine.core.embedding.EmbeddingService;
import com.thirdexploration.promengine.core.exception.ModelUnavailableException;
import com.thirdexploration.promengine.model.config.ModelGatewayProperties;
import com.thirdexploration.promengine.model.gateway.circuit.CircuitBreaker;
import com.thirdexploration.promengine.model.routing.LoadAwareRouter;
import com.thirdexploration.promengine.model.routing.SemanticRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultModelGateway implements ModelGateway {

    private final ProviderRegistry providerRegistry;
    private final SemanticRouter semanticRouter;
    private final LoadAwareRouter loadAwareRouter;
    private final ModelGatewayProperties properties;
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    // 在 DefaultModelGateway 中增加字段
    private final SemanticCache semanticCache;
    private final EmbeddingService embeddingService; // 需定义接口


    @Override
    public CompletionResult complete(CompletionRequest request) {
        // 1. 生成提示词向量
        float[] queryVector = embeddingService.embed(request.getPrompt());
        // 2. 查缓存
        CompletionResult cached = semanticCache.get(request.getPrompt(), queryVector);
        if (cached != null) {
            log.debug("Semantic cache hit for prompt: {}", request.getPrompt().substring(0, Math.min(50, request.getPrompt().length())));
            return cached;
        }
        // 3. 正常路由调用
        String selectedProviderId = selectProvider(request);
        ModelAdapter adapter = providerRegistry.getAdapter(selectedProviderId);
        if (adapter == null) throw new ModelUnavailableException("No available provider for request");
        CircuitBreaker cb = circuitBreakers.computeIfAbsent(selectedProviderId, id -> new CircuitBreaker(id, properties.getCircuitBreaker()));
        try {
            CompletionResult result = cb.execute(() -> adapter.complete(request));
            // 4. 存入缓存
            semanticCache.put(request.getPrompt(), queryVector, result);
            return result;
        } catch (Exception e) {
            log.warn("Provider {} failed, attempting fallback", selectedProviderId, e);
            return executeWithFallback(request, selectedProviderId);
        }
    }

    @Override
    public Stream<CompletionChunk> stream(CompletionRequest request) {
        String providerId = selectProvider(request);
        ModelAdapter adapter = providerRegistry.getAdapter(providerId);
        if (adapter == null) throw new ModelUnavailableException("No provider");
        return adapter.stream(request);
    }

    @Override
    public boolean isAvailable(String modelId) {
        return providerRegistry.findProviderByModel(modelId).isPresent();
    }

    @Override
    public void addFallbackChain(List<String> modelIds) {
        // 动态调整降级链
    }

    @Override
    public CostEstimate estimateCost(CompletionRequest request) {
        return providerRegistry.estimateCost(request.getModelId(), request.getPrompt());
    }

    @Override
    public Map<String, ModelLoadInfo> getLoadInfo() {
        return loadAwareRouter.getLoadInfo();
    }

    private String selectProvider(CompletionRequest request) {
        // 综合语义路由和负载感知
        String semanticChoice = semanticRouter.select(request);
        if (properties.getRouting().isLoadAware()) {
            return loadAwareRouter.adjust(semanticChoice, request);
        }
        return semanticChoice;
    }

    private CompletionResult executeWithFallback(CompletionRequest request, String failedProvider) {
        List<String> fallbackChain = properties.getRouting().getFallbackChain();
        for (String providerId : fallbackChain) {
            if (providerId.equals(failedProvider)) continue;
            try {
                ModelAdapter adapter = providerRegistry.getAdapter(providerId);
                if (adapter != null) {
                    return adapter.complete(request);
                }
            } catch (Exception e) {
                log.warn("Fallback provider {} failed", providerId, e);
            }
        }
        throw new ModelUnavailableException("All providers failed for request");
    }
}