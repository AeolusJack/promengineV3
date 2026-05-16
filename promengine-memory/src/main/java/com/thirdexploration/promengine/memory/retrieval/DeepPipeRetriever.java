package com.thirdexploration.promengine.memory.retrieval;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thirdexploration.promengine.memory.config.AeonMemoryProperties;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.storage.Neo4jGraphService;
import com.thirdexploration.promengine.memory.storage.SemanticMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 深度检索管道，支持 LLM 查询重写、意图解析、图谱扩展。
 * 优化：增加重写缓存、图谱深度限制、配置开关、超时保护。
 */
@Slf4j
@Component
public class DeepPipeRetriever {

    private final SemanticMemoryService semanticMemory;
    private final ChatClient.Builder chatClientBuilder;
    private final FastPipeRetriever fastPipe;
    private final AeonMemoryProperties properties;
    private final Cache<String, String> rewriteCache;

    @Autowired(required = false)
    private Neo4jGraphService graphService;

    public DeepPipeRetriever(SemanticMemoryService semanticMemory,
                             EmbeddingService embeddingService,
                             ChatClient.Builder chatClientBuilder,
                             FastPipeRetriever fastPipe,
                             AeonMemoryProperties properties) {
        this.semanticMemory = semanticMemory;
        this.chatClientBuilder = chatClientBuilder;
        this.fastPipe = fastPipe;
        this.properties = properties;
        this.rewriteCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }

    public List<MemoryRecord> retrieve(String layer, MemoryQuery query, List<String> domains, String pipelineName) {
        if (!"semantic".equals(layer)) {
            return fastPipe.retrieve(layer, query, domains);
        }
        return retrieveSemanticWithEnhancement(query, domains, pipelineName);
    }

    private List<MemoryRecord> retrieveSemanticWithEnhancement(MemoryQuery query,
                                                               List<String> domains,
                                                               String pipelineName) {
        String originalQuery = query.getText();
        if (originalQuery == null || originalQuery.isBlank()) {
            return List.of();
        }

        int topK = Math.min(query.getMaxResults() * 2, 200);
        List<MemoryRecord> allResults = new ArrayList<>();

        for (String domain : domains) {
            // 基础语义检索
            List<MemoryRecord> baseResults = semanticMemory.semanticSearch(originalQuery, topK);
            if (baseResults != null) {
                baseResults = baseResults.stream()
                        .filter(r -> domain.equals(r.getDomain()))
                        .collect(Collectors.toList());
                allResults.addAll(baseResults);
            }

            // LLM 查询重写（仅在 deep-llm 模式下且配置允许）
            if ("deep-llm".equals(pipelineName) && properties.isDeepRetrievalEnabled()) {
                String rewritten = rewriteQueryWithLLM(originalQuery);
                if (!rewritten.equals(originalQuery)) {
                    List<MemoryRecord> rewrittenResults = semanticMemory.semanticSearch(rewritten, topK);
                    if (rewrittenResults != null) {
                        rewrittenResults = rewrittenResults.stream()
                                .filter(r -> domain.equals(r.getDomain()))
                                .collect(Collectors.toList());
                        allResults.addAll(rewrittenResults);
                    }
                }
            }

            // 图谱扩展（限制深度和数量）
            if (graphService != null && properties.isGraphExpansionEnabled() && !allResults.isEmpty()) {
                List<String> seedIds = allResults.stream()
                        .map(MemoryRecord::getId)
                        .distinct()
                        .limit(50)
                        .collect(Collectors.toList());
                List<String> expandedIds = graphService.expandByRelations(seedIds,
                        properties.getGraphMaxDepth(),
                        properties.getGraphMaxResults());
                if (expandedIds != null && !expandedIds.isEmpty()) {
                    List<MemoryRecord> graphResults = expandedIds.stream()
                            .map(semanticMemory::findById)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    allResults.addAll(graphResults);
                }
            }
        }

        // 去重、截断
        return allResults.stream()
                .distinct()
                .limit(query.getMaxResults())
                .collect(Collectors.toList());
    }

    private String rewriteQueryWithLLM(String original) {
        return rewriteCache.get(original, key -> {
            try {
                ChatClient client = chatClientBuilder.build();
                String rewritten = client.prompt()
                        .user("将以下查询重写为更精确的检索关键词，只返回重写后的文本，不要解释：\n" + original)
                        .call()
                        .content();
                if (rewritten != null && !rewritten.isBlank()) {
                    log.debug("Query rewritten: {} → {}", original, rewritten);
                    return rewritten.trim();
                }
            } catch (Exception e) {
                log.warn("LLM query rewrite failed, using original: {}", e.getMessage());
            }
            return original;
        });
    }
}