//package com.thirdexploration.promengine.memory.storage;
//
//import org.springframework.ai.document.Document;
//import org.springframework.ai.vectorstore.SearchRequest;
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Component
//public class SpringAIVectorStorage implements VectorStorage {
//
//    private final VectorStore vectorStore;
//
//    public SpringAIVectorStorage(VectorStore vectorStore) {
//        this.vectorStore = vectorStore;
//    }
//
//    @Override
//    public void add(String id, float[] vector, String metadataJson) {
//        Document doc = new Document(id, Map.of("metadata", metadataJson));
//        vectorStore.add(List.of(doc));
//    }
//
//    @Override
//    public void batchAdd(List<VectorRecord> records) {
//        List<Document> docs = records.stream()
//                .map(r -> new Document(r.id(), Map.of("metadata", r.metadata())))
//                .toList();
//        vectorStore.add(docs);
//    }
//
//    @Override
//    public List<SearchHit> search(float[] queryVector, int topK) {
//        throw new UnsupportedOperationException("Use similaritySearch with text query");
//    }
//
//    /**
//     * 按文本查询（推荐方式）
//     */
//    public List<SearchHit> search(String queryText, int topK) {
//        // M7 版本使用 Builder 模式构建 SearchRequest
//        SearchRequest request = SearchRequest.builder()
//                .query(queryText)
//                .topK(topK)
//                .build();
//
//        List<Document> results = vectorStore.similaritySearch(request);
//
//        return results.stream()
//                .map(doc -> {
//                    // M7 中 getScore() 返回 Double 对象，需安全拆箱
//                    Double score = doc.getScore();
//                    float scoreValue = (score != null) ? score.floatValue() : 0.0f;
//                    return new SearchHit(doc.getId(), scoreValue);
//                })
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public void delete(String id) {
//        vectorStore.delete(List.of(id));
//    }
//
//    @Override
//    public void rebuildIndex() {
//        // Spring AI 抽象层无统一索引重建方法
//    }
//}