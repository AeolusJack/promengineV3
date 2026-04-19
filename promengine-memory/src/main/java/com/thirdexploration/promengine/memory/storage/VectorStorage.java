package com.thirdexploration.promengine.memory.storage;

import java.util.List;

/**
 * 向量存储接口，用于语义检索。
 */
public interface VectorStorage {

    /**
     * 添加向量记录。
     */
    void add(String id, float[] vector, String metadataJson);

    /**
     * 批量添加。
     */
    void batchAdd(List<VectorRecord> records);

    /**
     * 根据查询向量检索最相似的 TopK 记录 ID。
     */
    List<SearchHit> search(float[] queryVector, int topK);

    /**
     * 删除记录。
     */
    void delete(String id);

    /**
     * 重建索引。
     */
    void rebuildIndex();

    record VectorRecord(String id, float[] vector, String metadata) {}
    record SearchHit(String id, float score) {}
}