package com.thirdexploration.promengine.model.routing;

import com.thirdexploration.promengine.core.ModelGateway;
import com.thirdexploration.promengine.core.domain.CompletionRequest;
import com.thirdexploration.promengine.model.config.ModelGatewayProperties;
import com.thirdexploration.promengine.model.gateway.ModelAdapter;
import com.thirdexploration.promengine.model.gateway.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class LoadAwareRouter {

    private final ModelGatewayProperties properties;
    private final ProviderRegistry providerRegistry;
    private final Map<String, LoadInfo> loadInfoMap = new ConcurrentHashMap<>();

    public void recordBefore(String providerId) {
        loadInfoMap.computeIfAbsent(providerId, k -> new LoadInfo()).incrementQueue();
    }

    public void recordAfter(String providerId, long latencyMs) {
        LoadInfo info = loadInfoMap.get(providerId);
        if (info != null) {
            info.decrementQueue();
            info.recordLatency(latencyMs);
        }
    }

    public String adjust(String preferredProvider, CompletionRequest request) {
        if ("local-ollama".equals(preferredProvider) && isProviderAvailable("local-ollama")) {
            return "local-ollama";
        }
        if (request.getPrompt().length() < 200 && isProviderAvailable("local-ollama")) {
            return "local-ollama";
        }
        LoadInfo info = loadInfoMap.get(preferredProvider);
        if (info != null && info.isOverloaded()) {
            if (isProviderAvailable("local-ollama")) return "local-ollama";
            List<String> fallback = properties.getRouting().getFallbackChain();
            if (!fallback.isEmpty()) return fallback.get(0);
        }
        return preferredProvider;
    }

    private boolean isProviderAvailable(String providerId) {
        ModelAdapter adapter = providerRegistry.getAdapter(providerId);
        return adapter != null && adapter.isAvailable();
    }


    public Map<String, ModelGateway.ModelLoadInfo> getLoadInfo() {
        Map<String, ModelGateway.ModelLoadInfo> snapshot = new HashMap<>();
        loadInfoMap.forEach((key, value) -> snapshot.put(key, value));
        return Collections.unmodifiableMap(snapshot);
    }

    // 非静态内部类，可访问外部类的 properties
    private class LoadInfo implements ModelGateway.ModelLoadInfo {
        private final AtomicInteger queueLength = new AtomicInteger(0);
        private final AtomicLong totalLatencyMs = new AtomicLong(0);
        private final AtomicLong callCount = new AtomicLong(0);

        void incrementQueue() { queueLength.incrementAndGet(); }
        void decrementQueue() { queueLength.decrementAndGet(); }

        void recordLatency(long latencyMs) {
            totalLatencyMs.addAndGet(latencyMs);
            callCount.incrementAndGet();
        }

        @Override
        public int getQueueLength() {
            return queueLength.get();
        }

        @Override
        public double getAverageLatency() {
            long calls = callCount.get();
            return calls == 0 ? 0 : (double) totalLatencyMs.get() / calls;
        }

        @Override
        public boolean isOverloaded() {
            // 现在可以访问外部类的 properties
            return queueLength.get() > properties.getRouting().getLocalModelMaxQueue();
        }
    }
}