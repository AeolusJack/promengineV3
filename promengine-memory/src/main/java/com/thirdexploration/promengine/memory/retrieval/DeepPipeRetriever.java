package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.storage.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * aeon
 * 深度检索管道，可选 LLM 增强，支持查询重写、意图解析、跨域推理。
 */
@Slf4j
@Component
//@RequiredArgsConstructor
public class DeepPipeRetriever {

    private final SemanticMemoryService semanticMemory;
    private final EmbeddingService embeddingService;
    private final ChatClient.Builder chatClientBuilder;
    // 非必需依赖，当图谱功能未启用时可以为 null
    @Autowired(required = false)
    private  Neo4jGraphService graphService;
    private final FastPipeRetriever fastPipe;
    // 显式构造器，不包含 graphService
    public DeepPipeRetriever(SemanticMemoryService semanticMemory,
                             EmbeddingService embeddingService,
                             ChatClient.Builder chatClientBuilder,
                             FastPipeRetriever fastPipe) {
        this.semanticMemory = semanticMemory;
        this.embeddingService = embeddingService;
        this.chatClientBuilder = chatClientBuilder;
        this.fastPipe = fastPipe;
    }
    public List<MemoryRecord> retrieve(String layer, MemoryQuery query, List<String> domains, String pipelineName) {
        // 对于非语义层，回退到 FastPipe
        if (!"semantic".equals(layer)) {
            return fastPipe.retrieve(layer, query, domains);
        }
        return retrieveSemanticWithEnhancement(query, domains, pipelineName);
    }

    private List<MemoryRecord> retrieveSemanticWithEnhancement(MemoryQuery query, List<String> domains, String pipelineName) {
        List<MemoryRecord> allResults = new ArrayList<>();

        for (String domain : domains) {
            // 1. 初始向量检索
            float[] queryVector = embeddingService.embed( query.getText().length() > 300 ? query.getText().substring(0,300) : query.getText());
            List<MemoryRecord> vectorResults = semanticMemory.semanticSearch(queryVector, query.getMaxResults() * 2);

            // 2. 如果启用深度增强
            if ("deep-llm".equals(pipelineName)) {
                // 查询重写
                String rewritten = rewriteQueryWithLLM(query.getText());
                float[] rewrittenVector = embeddingService.embed(rewritten);
                if (rewrittenVector != null){
                    List<MemoryRecord> memoryRecords = semanticMemory.semanticSearch(rewrittenVector, query.getMaxResults());
                    if (memoryRecords != null && !memoryRecords.isEmpty()){
                        vectorResults.addAll(memoryRecords);
                    }
                }
                // 意图解析与扩展（略）
            }

            // 3. 图谱扩展
            if (graphService != null) {
                List<String> seedIds = vectorResults.stream().map(MemoryRecord::getId).toList();
                List<String> expandedIds = graphService.expandByRelations(seedIds);
                List<MemoryRecord> byIds = semanticMemory.findByIds(expandedIds);
                if (byIds != null && !byIds.isEmpty()){
                    vectorResults.addAll(byIds);
                }
            }
             allResults.addAll(vectorResults);
        }

        // 去重
        return allResults.stream().distinct().limit(query.getMaxResults()).toList();
    }

    private String rewriteQueryWithLLM(String original) {
        try {
            ChatClient client = chatClientBuilder.build();
            return client.prompt()
                    .user("将以下查询重写为更精确的检索关键词，只返回重写后的文本，不要解释：\n" + original)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("LLM query rewrite failed, using original", e);
            return original;
        }
    }
}