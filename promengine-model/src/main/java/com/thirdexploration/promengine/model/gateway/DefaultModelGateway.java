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

    private final SemanticCache semanticCache;
    private final EmbeddingService embeddingService;

    @Override
    public CompletionResult complete(CompletionRequest request) {
        // 语义缓存
        float[] queryVector = embeddingService.embed(request.getPrompt());
        CompletionResult cached = semanticCache.get(request.getPrompt(), queryVector);
        if (cached != null) {
            log.debug("Semantic cache hit");
            return cached;
        }

        String selectedProviderId = selectProvider(request);
        ModelAdapter adapter = providerRegistry.getAdapter(selectedProviderId);
        if (adapter == null) {
            throw new ModelUnavailableException("No available provider for request");
        }

        CircuitBreaker cb = circuitBreakers.computeIfAbsent(selectedProviderId,
                id -> new CircuitBreaker(id, properties.getCircuitBreaker()));

        loadAwareRouter.recordBefore(selectedProviderId);
        long start = System.currentTimeMillis();
        try {
            CompletionResult result = cb.execute(() -> adapter.complete(request));
            long latency = System.currentTimeMillis() - start;
            loadAwareRouter.recordAfter(selectedProviderId, latency);

            semanticCache.put(request.getPrompt(), queryVector, result);
            return result;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            loadAwareRouter.recordAfter(selectedProviderId, latency);
            log.warn("Provider {} failed, attempting fallback", selectedProviderId, e);
            return executeWithFallback(request, selectedProviderId);
        }
    }

    @Override
    public Stream<CompletionChunk> stream(CompletionRequest request) {
        String selectedProviderId = selectProvider(request);
        ModelAdapter adapter = providerRegistry.getAdapter(selectedProviderId);
        if (adapter == null) {
            throw new ModelUnavailableException("No available provider for streaming");
        }

        loadAwareRouter.recordBefore(selectedProviderId);
        try {
            Stream<CompletionChunk> chunkStream = adapter.stream(request);
            // 当流关闭时记录负载（延迟未知，设为0）
            chunkStream.onClose(() -> loadAwareRouter.recordAfter(selectedProviderId, 0));
            return chunkStream;
        } catch (Exception e) {
            loadAwareRouter.recordAfter(selectedProviderId, 0);
            log.warn("Stream failed for provider {}, attempting fallback", selectedProviderId, e);
            return executeStreamFallback(request, selectedProviderId);
        }
    }

    @Override
    public boolean isAvailable(String modelId) {
        return providerRegistry.findProviderByModel(modelId).isPresent();
    }

    @Override
    public void addFallbackChain(List<String> modelIds) {
        properties.getRouting().setFallbackChain(modelIds);
    }

    @Override
    public CostEstimate estimateCost(CompletionRequest request) {
        return providerRegistry.estimateCost(request.getModelId(), request.getPrompt());
    }

    @Override
    public Map<String, ModelLoadInfo> getLoadInfo() {
        return loadAwareRouter.getLoadInfo();
    }

    // ------------------- 内部方法 -------------------

    private String selectProvider(CompletionRequest request) {
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
            ModelAdapter adapter = providerRegistry.getAdapter(providerId);
            if (adapter == null) continue;

            loadAwareRouter.recordBefore(providerId);
            long start = System.currentTimeMillis();
            try {
                CompletionResult result = adapter.complete(request);
                long latency = System.currentTimeMillis() - start;
                loadAwareRouter.recordAfter(providerId, latency);
                return result;
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                loadAwareRouter.recordAfter(providerId, latency);
                log.warn("Fallback provider {} also failed", providerId, e);
            }
        }
        throw new ModelUnavailableException("All providers failed for request");
    }

    private Stream<CompletionChunk> executeStreamFallback(CompletionRequest request, String failedProvider) {
        List<String> fallbackChain = properties.getRouting().getFallbackChain();
        for (String providerId : fallbackChain) {
            if (providerId.equals(failedProvider)) continue;
            ModelAdapter adapter = providerRegistry.getAdapter(providerId);
            if (adapter == null) continue;

            loadAwareRouter.recordBefore(providerId);
            try {
                Stream<CompletionChunk> chunkStream = adapter.stream(request);
                chunkStream.onClose(() -> loadAwareRouter.recordAfter(providerId, 0));
                return chunkStream;
            } catch (Exception e) {
                loadAwareRouter.recordAfter(providerId, 0);
                log.warn("Fallback stream provider {} failed", providerId, e);
            }
        }
        throw new ModelUnavailableException("All streaming providers failed");
    }
}