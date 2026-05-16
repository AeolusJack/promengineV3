package com.thirdexploration.promengine.agent.common.rag;

import com.thirdexploration.promengine.core.agent.ContextProvider;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final UnifiedMemoryAPI memoryAPI;
    private final ChatClient.Builder chatClientBuilder;
    private final List<ContextProvider> contextProviders;

    /**
     * @param query        用户问题
     * @param domainFilters 检索记忆域过滤 (可为空)
     * @param maxResults   记忆条数
     * @param includeRealTime 是否注入 ContextProvider 实时数据
     */
    public String answer(String query, List<String> domainFilters, int maxResults, boolean includeRealTime) {
        MemoryQuery.MemoryQueryBuilder queryBuilder = MemoryQuery.builder()
                .text(query)
                .maxResults(maxResults)
                .includeWorking(false)
                .includeEpisodic(false)
                .includeSemantic(true);
        if (domainFilters != null && !domainFilters.isEmpty()) {
            queryBuilder.domains(domainFilters);
        }
        List<MemoryEntry> memories = memoryAPI.recall(queryBuilder.build());

        Map<String, Object> extra = Collections.emptyMap();
        if (includeRealTime) {
            extra = new HashMap<>();
            for (ContextProvider provider : contextProviders) {
                extra.putAll(provider.collect(null, null, null));
            }
        }

        String prompt = buildPrompt(query, memories, extra);
        return chatClientBuilder.build().prompt(prompt).call().content();
    }

    private String buildPrompt(String query, List<MemoryEntry> memories, Map<String, Object> extra) {
        StringBuilder sb = new StringBuilder("基于以下信息回答问题。\n\n");
        if (!memories.isEmpty()) {
            sb.append("【知识库】\n");
            memories.forEach(m -> sb.append("- ").append(summarize(m)).append("\n"));
        }
        if (!extra.isEmpty()) {
            sb.append("\n【实时数据】\n");
            extra.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
        }
        sb.append("\n【问题】\n").append(query).append("\n\n【答案】");
        return sb.toString();
    }

    private String summarize(MemoryEntry m) {
        String s = m.getSummary() != null ? m.getSummary() : m.getContent();
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}