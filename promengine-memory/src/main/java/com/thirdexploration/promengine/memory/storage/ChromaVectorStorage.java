package com.thirdexploration.promengine.memory.storage;

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

    private final VectorStore vectorStore; // Spring AI 自动注入

    @Override
    public void add(String id, float[] vector, String metadataJson) {
        Document doc = new Document(id, metadataJson, Map.of());
        vectorStore.add(List.of(doc));
        log.debug("Added vector for id={}", id);
    }

    @Override
    public void batchAdd(List<VectorRecord> records) {
        List<Document> docs = records.stream()
                .map(r -> new Document(r.id(), r.metadata(), Map.of()))
                .toList();
        vectorStore.add(docs);
        log.info("Batch added {} vectors", records.size());
    }

    @Override
    public List<SearchHit> search(float[] queryVector, int topK) {
        throw new UnsupportedOperationException("Use text-based search instead");
    }

    /**
     * 按文本查询（推荐方式）
     */
    public List<SearchHit> search(String queryText, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(queryText)
                .topK(topK)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        return results.stream()
                .map(doc -> {
                    Double score = doc.getScore();
                    float scoreValue = score != null ? score.floatValue() : 0.0f;
                    return new SearchHit(doc.getId(), scoreValue);
                })
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        vectorStore.delete(List.of(id));
        log.debug("Deleted vector for id={}", id);
    }

    @Override
    public void rebuildIndex() {
        log.info("Index rebuild not supported in Spring AI abstraction");
    }
}