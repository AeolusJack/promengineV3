package com.thirdexploration.promengine.memory.retrieval;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 深度检索管道，支持 LLM 查询重写、意图解析、图谱扩展等增强特性。
 * 仅对语义记忆层生效，其他层回退到 FastPipe。
 */
@Slf4j
@Component
public class DeepPipeRetriever {

    private final SemanticMemoryService semanticMemory;
    private final ChatClient.Builder chatClientBuilder;
    private final FastPipeRetriever fastPipe;

    @Autowired(required = false)
    private Neo4jGraphService graphService;

    public DeepPipeRetriever(SemanticMemoryService semanticMemory,
                             EmbeddingService embeddingService, // 保留以备未来使用，当前不依赖
                             ChatClient.Builder chatClientBuilder,
                             FastPipeRetriever fastPipe) {
        this.semanticMemory = semanticMemory;
        this.chatClientBuilder = chatClientBuilder;
        this.fastPipe = fastPipe;
    }

    /**
     * 根据层级和查询选择检索策略。
     */
    public List<MemoryRecord> retrieve(String layer, MemoryQuery query, List<String> domains, String pipelineName) {
        if (!"semantic".equals(layer)) {
            return fastPipe.retrieve(layer, query, domains);
        }
        return retrieveSemanticWithEnhancement(query, domains, pipelineName);
    }

    /**
     * 带增强的语义记忆检索。
     */
    private List<MemoryRecord> retrieveSemanticWithEnhancement(MemoryQuery query,
                                                               List<String> domains,
                                                               String pipelineName) {
        String originalQuery = query.getText();
        if (originalQuery == null || originalQuery.isBlank()) {
            return List.of();
        }

        int topK = query.getMaxResults() * 2; // 初始放宽召回量，后续融合和去重
        List<MemoryRecord> allResults = new ArrayList<>();

        for (String domain : domains) {
            // 1. 基础文本语义搜索（底层向量存储自行处理 embed 和搜索）
            List<MemoryRecord> baseResults = semanticMemory.semanticSearch(originalQuery, topK);
            if (baseResults == null) {
                baseResults = List.of();
            } else {
                // 过滤出当前 domain 的记录（如果 semanticMemory 未按 domain 过滤）
                baseResults = baseResults.stream()
                        .filter(r -> domain.equals(r.getDomain()))
                        .collect(Collectors.toList());
            }
            allResults.addAll(baseResults);

            // 2. LLM 增强：查询重写与扩展
            if ("deep-llm".equals(pipelineName)) {
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
                // 意图解析与扩展可在此处加入
            }

            // 3. 图谱扩展
            if (graphService != null) {
                List<String> seedIds = allResults.stream()
                        .map(MemoryRecord::getId)
                        .distinct()
                        .collect(Collectors.toList());
                if (!seedIds.isEmpty()) {
                    List<String> expandedIds = graphService.expandByRelations(seedIds);
                    if (expandedIds != null && !expandedIds.isEmpty()) {
                        List<MemoryRecord> graphResults = expandedIds.stream()
                                .map(semanticMemory::findById)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                        allResults.addAll(graphResults);
                    }
                }
            }
        }

        // 4. 去重、截断
        return allResults.stream()
                .distinct()
                .limit(query.getMaxResults())
                .collect(Collectors.toList());
    }

    /**
     * 使用小模型重写查询，使其更适合检索。
     */
    private String rewriteQueryWithLLM(String original) {
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
            log.warn("LLM query rewrite failed, using original query: {}", e.getMessage());
        }
        return original;
    }
}