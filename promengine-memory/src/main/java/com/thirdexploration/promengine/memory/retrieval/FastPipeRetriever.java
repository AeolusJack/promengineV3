package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.storage.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 快速检索管道，零 LLM 调用。
 * 优化：episodic检索优先使用 Lucene，避免全量加载。
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
        // 优先使用 Lucene 全文检索
        if (query.getText() != null && !query.getText().isBlank()) {
            List<String> ids = luceneService.searchEpisodic(query.getText(), query.getMaxResults() * 2);
            if (!ids.isEmpty()) {
                List<MemoryRecord> results = episodicMemory.findByIds(ids);
                // 按domain和时间范围二次过滤
                Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
                return results.stream()
                        .filter(r -> domains.contains(r.getDomain()))
                        .filter(r -> r.getTimestamp().isAfter(from))
                        .limit(query.getMaxResults())
                        .toList();
            }
        }
        // 回退到时间范围查询
        Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant to = Instant.now();
        List<MemoryRecord> results = new ArrayList<>();
        for (String domain : domains) {
            results.addAll(episodicMemory.queryByTimeRange(
                    query.getUserId(), domain, from, to, query.getMaxResults(), query.getProjectId()));
        }
        return results.stream().limit(query.getMaxResults()).toList();
    }

    private List<MemoryRecord> retrieveSemantic(MemoryQuery query, List<String> domains) {
        if (query.getText() != null && !query.getText().isBlank()) {
            List<String> ids = luceneService.searchSemantic(query.getText(), query.getMaxResults());
            return semanticMemory.findByIds(ids);
        }
        return List.of();
    }

    private List<MemoryRecord> retrieveProcedural(MemoryQuery query, List<String> domains) {
        String trigger = query.getText();
        List<MemoryRecord> results = new ArrayList<>();
        for (String domain : domains) {
            results.addAll(proceduralMemory.findByTrigger(query.getUserId(), domain, trigger, query.getMaxResults()));
        }
        return results;
    }

    private List<MemoryRecord> retrieveCollective(MemoryQuery query, List<String> domains) {
        String level = query.getMinSharingLevel() != null ? query.getMinSharingLevel() : "domain";
        List<MemoryRecord> results = new ArrayList<>();
        for (String domain : domains) {
            results.addAll(collectiveMemory.queryShared(domain, level, query.getMaxResults()));
        }
        return results;
    }
}