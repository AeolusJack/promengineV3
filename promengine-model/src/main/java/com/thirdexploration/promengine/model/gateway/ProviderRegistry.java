package com.thirdexploration.promengine.model.gateway;

import com.thirdexploration.promengine.core.domain.CostEstimate;
import com.thirdexploration.promengine.model.config.ModelGatewayProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ProviderRegistry {

    private final List<ModelAdapter> adapters;
    private final ModelGatewayProperties properties;
    private final Map<String, ModelAdapter> adapterMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (ModelAdapter adapter : adapters) {
            adapterMap.put(adapter.getProviderId(), adapter);
        }
    }

    public ModelAdapter getAdapter(String providerId) {
        return adapterMap.get(providerId);
    }

    public Optional<String> findProviderByModel(String modelName) {
        return properties.getProviders().stream()
                .filter(p -> p.getModels().stream().anyMatch(m -> m.getName().equals(modelName)))
                .map(ModelGatewayProperties.ProviderConfig::getId)
                .findFirst();
    }

    public CostEstimate estimateCost(String modelId, String prompt) {
        // 简化实现，实际可调用第三方价格 API
        return CostEstimate.builder().estimatedCost(0.001).currency("USD").build();
    }
}