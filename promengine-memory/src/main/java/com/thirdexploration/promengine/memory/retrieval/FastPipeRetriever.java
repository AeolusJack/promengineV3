package com.thirdexploration.promengine.memory.retrieval;


import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.storage.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * aeon
 * 快速检索管道，零 LLM 调用，基于关键词、时间范围和元数据过滤。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FastPipeRetriever {

    private final WorkingMemoryManager workingMemory;
    private final EpisodicMemoryService episodicMemory;
    private final SemanticMemoryService semanticMemory;
    private final ProceduralMemoryService proceduralMemory;
    private final CollectiveMemoryService collectiveMemory;
    private final LuceneIndexService luceneService;

    public List<MemoryRecord> retrieveWorking(MemoryQuery query) {
        return workingMemory.queryBySession(query.getSessionId(), query.getText(), query.getMaxResults());
    }

    public List<MemoryRecord> retrieve(String layer, MemoryQuery query, List<String> domains) {
        return switch (layer) {
            case "episodic" -> retrieveEpisodic(query, domains);
            case "semantic" -> retrieveSemantic(query, domains);
            case "procedural" -> retrieveProcedural(query, domains);
            case "collective" -> retrieveCollective(query, domains);
            default -> List.of();
        };
    }

    private List<MemoryRecord> retrieveEpisodic(MemoryQuery query, List<String> domains) {
        List<MemoryRecord> results = new ArrayList<>();
        Instant from = Instant.now().minusSeconds(7 * 24 * 3600);
        Instant to = Instant.now();

        for (String domain : domains) {
            List<MemoryRecord> domainResults = episodicMemory.queryByTimeRange(
                    query.getUserId(), domain, from, to, query.getMaxResults() , query.getProjectId());
            results.addAll(domainResults);
        }

        // 如果有文本查询，使用 Lucene 做关键词过滤
        if (query.getText() != null && !query.getText().isBlank()) {
            List<String> ids = luceneService.searchEpisodic(query.getText(), query.getMaxResults());
            results = results.stream()
                    .filter(r -> ids.contains(r.getId()))
                    .toList();
        }

        return results.stream().limit(query.getMaxResults()).toList();
    }

    private List<MemoryRecord> retrieveSemantic(MemoryQuery query, List<String> domains) {
        // 语义记忆的快速检索主要依靠 Lucene 关键词索引
        if (query.getText() != null && !query.getText().isBlank()) {
            List<String> ids = luceneService.searchSemantic(query.getText(), query.getMaxResults());
            return semanticMemory.findByIds(ids);
        }
        return List.of();
    }

    private List<MemoryRecord> retrieveProcedural(MemoryQuery query, List<String> domains) {
        List<MemoryRecord> results = new ArrayList<>();
        String trigger = query.getText();
        for (String domain : domains) {
            results.addAll(proceduralMemory.findByTrigger(query.getUserId(), domain, trigger, query.getMaxResults()));
        }
        return results;
    }

    private List<MemoryRecord> retrieveCollective(MemoryQuery query, List<String> domains) {
        List<MemoryRecord> results = new ArrayList<>();
        String level = query.getMinSharingLevel() != null ? query.getMinSharingLevel() : "domain";
        for (String domain : domains) {
            results.addAll(collectiveMemory.queryShared(domain, level, query.getMaxResults()));
        }
        return results;
    }
}