package com.thirdexploration.promengine.model.routing;

import com.thirdexploration.promengine.core.ModelGateway;
import com.thirdexploration.promengine.core.domain.CompletionRequest;
import com.thirdexploration.promengine.model.config.ModelGatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class LoadAwareRouter {

    private final ModelGatewayProperties properties;
    private final Map<String, LoadInfo> loadInfoMap = new ConcurrentHashMap<>();

    public String adjust(String preferredProvider, CompletionRequest request) {
        LoadInfo info = loadInfoMap.computeIfAbsent(preferredProvider, k -> new LoadInfo());
        if (info.isOverloaded() && !preferredProvider.equals("local-ollama")) {
            // 降级到本地
            return "local-ollama";
        }
        return preferredProvider;
    }

    public Map<String, ModelGateway.ModelLoadInfo> getLoadInfo() {
        // 转换内部 LoadInfo 为接口类型
        return Map.of();
    }

    private static class LoadInfo implements ModelGateway.ModelLoadInfo {
        private final int queueLength = 0;
        @Override public int getQueueLength() { return queueLength; }
        @Override public double getAverageLatency() { return 100; }
        @Override public boolean isOverloaded() { return queueLength > 3; }
    }
}