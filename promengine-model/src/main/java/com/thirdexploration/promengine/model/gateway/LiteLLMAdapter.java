package com.thirdexploration.promengine.model.gateway;

import com.thirdexploration.promengine.core.domain.CompletionChunk;
import com.thirdexploration.promengine.core.domain.CompletionRequest;
import com.thirdexploration.promengine.core.domain.CompletionResult;
import com.thirdexploration.promengine.model.client.OpenAIClient;
import com.thirdexploration.promengine.model.config.ModelGatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class LiteLLMAdapter implements ModelAdapter {

    private final OpenAIClient openAIClient;
    private final ModelGatewayProperties properties;

    @Override
    public String getProviderId() {
        return "litellm";
    }

    @Override
    public CompletionResult complete(CompletionRequest request) {
        // LiteLLM 通常兼容 OpenAI API
        return openAIClient.complete(getEndpoint(), getApiKey(), request);
    }

    @Override
    public Stream<CompletionChunk> stream(CompletionRequest request) {
        return openAIClient.stream(getEndpoint(), getApiKey(), request);
    }

    @Override
    public boolean isAvailable() {
        return true; // 可由健康检查实现
    }

    private String getEndpoint() {
        return properties.getProviders().stream()
                .filter(p -> "litellm".equals(p.getId()))
                .findFirst()
                .map(ModelGatewayProperties.ProviderConfig::getEndpoint)
                .orElse("http://localhost:4000");
    }

    private String getApiKey() {
        return properties.getProviders().stream()
                .filter(p -> "litellm".equals(p.getId()))
                .findFirst()
                .map(ModelGatewayProperties.ProviderConfig::getApiKey)
                .orElse("");
    }
}