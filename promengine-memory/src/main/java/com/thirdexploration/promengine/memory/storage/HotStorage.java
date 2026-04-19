package com.thirdexploration.promengine.memory.storage;

import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.model.StoredMemoryEntry;

import java.time.Instant;
import java.util.List;

/**
 * 热存储（SQLite）操作接口。
 */
public interface HotStorage {

    /**
     * 插入一条记忆。
     */
    void insert(MemoryRecord record);

    /**
     * 批量插入。
     */
    void batchInsert(List<MemoryRecord> records);

    /**
     * 根据ID查询。
     */
    StoredMemoryEntry findById(String id);

    /**
     * 根据时间范围和用户查询。
     */
    List<StoredMemoryEntry> findByTimeRange(String userId, Instant from, Instant to, int limit);

    /**
     * 根据关键词模糊查询（用于降级检索）。
     */
    List<StoredMemoryEntry> searchByKeyword(String userId, String keyword, int limit);

    /**
     * 标记为软删除。
     */
    void softDelete(String id);

    /**
     * 物理删除。
     */
    void hardDelete(String id);

    /**
     * 迁移超出保留期的记录到温存储，返回迁移的记录列表。
     */
    List<StoredMemoryEntry> migrateToWarm(Instant beforeTimestamp);

    /**
     * 统计未删除的记录数。
     *
     * @return 记录总数
     */
    long countActive();
}