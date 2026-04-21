package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * aeon
 * 双核路由器：根据查询复杂度和层级选择合适的检索管道。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DualCoreRouter {

    private final FastPipeRetriever fastPipe;
    private final DeepPipeRetriever deepPipe;

    public List<MemoryRecord> route(String layer, MemoryQuery query, List<String> domains) {
        double complexity = estimateComplexity(query);
        log.debug("Routing layer={}, complexity={}", layer, complexity);

        // 工作记忆始终走 FastPipe
        if ("working".equals(layer)) {
            return fastPipe.retrieveWorking(query);
        }

        // 根据复杂度选择管道
        if (complexity < 0.3) {
            return fastPipe.retrieve(layer, query, domains);
        } else if (complexity < 0.7) {
            return deepPipe.retrieve(layer, query, domains, "light-llm");
        } else {
            return deepPipe.retrieve(layer, query, domains, "deep-llm");
        }
    }

    private double estimateComplexity(MemoryQuery query) {
        String text = query.getText();
        if (text == null || text.isBlank()) return 0.1;

        double score = Math.min(text.length() / 200.0, 0.5);

        String lower = text.toLowerCase();
        if (lower.contains("为什么") || lower.contains("原因")) score += 0.3;
        if (lower.contains("如何") || lower.contains("怎么")) score += 0.2;
        if (query.isCrossDomain()) score += 0.2;

        return Math.min(score, 1.0);
    }
}