package com.thirdexploration.promengine.memory.storage;

import com.thirdexploration.promengine.memory.exception.MemoryStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChromaVectorStorage implements VectorStorage {

    private final VectorStore vectorStore;

    @Override
    public void add(String id, float[] vector, String metadataJson) {
        try {
            Document doc = new Document(id, metadataJson, Map.of());
            vectorStore.add(List.of(doc));
            log.debug("Added vector for id={}", id);
        } catch (Exception e) {
            log.error("Failed to add vector for id={}", id, e);
            throw new MemoryStorageException("Vector add failed", e);
        }
    }

    @Override
    public void batchAdd(List<VectorRecord> records) {
        if (records.isEmpty()) return;
        try {
            List<Document> docs = records.stream()
                    .map(r -> new Document(r.id(), r.metadata(), Map.of()))
                    .toList();
            vectorStore.add(docs);
            log.info("Batch added {} vectors", records.size());
        } catch (Exception e) {
            log.error("Failed to batch add vectors, count={}", records.size(), e);
            throw new MemoryStorageException("Vector batch add failed", e);
        }
    }

    /**
     * 向量搜索 - 由于 Spring AI VectorStore 需要文本输入，这里委托给文本搜索。
     * 实际使用时，调用方应直接使用 search(String, int)。
     */
    @Override
    public List<SearchHit> search(float[] queryVector, int topK) {
        log.warn("Direct vector search not supported. Use text-based search instead.");
        return List.of();
    }

    /**
     * 文本语义搜索 - 推荐使用
     */
    public List<SearchHit> search(String queryText, int topK) {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(queryText)
                    .topK(topK)
                    .build();

            List<Document> results = vectorStore.similaritySearch(request);
            log.debug("Vector search for '{}' returned {} results", queryText, results.size());

            return results.stream()
                    .map(doc -> {
                        double score = doc.getScore() != null ? doc.getScore() : 0.0;
                        return new SearchHit(doc.getId(), (float) score);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Vector search failed for query: {}", queryText, e);
            return List.of(); // 降级返回空，不影响主流程
        }
    }

    @Override
    public void delete(String id) {
        try {
            vectorStore.delete(List.of(id));
            log.debug("Deleted vector for id={}", id);
        } catch (Exception e) {
            log.error("Failed to delete vector id={}", id, e);
        }
    }

    @Override
    public void rebuildIndex() {
        log.info("Index rebuild not supported in Spring AI Chroma abstraction");
    }
}