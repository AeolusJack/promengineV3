package com.thirdexploration.promengine.memory.storage;

import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.model.StoredMemoryEntry;

import java.time.Instant;
import java.util.List;

/**
 * 温存储（Parquet + JSONL 摘要）操作接口。
 */
public interface WarmStorage {

    /**
     * 批量写入记录到温存储。
     * 每条记录会同时追加到 Parquet 文件和 JSONL 摘要文件。
     *
     * @param records 待写入记录列表
     * @param partitionMonth 分区月份，格式 yyyy-MM
     */
    void append(List<StoredMemoryEntry> records, String partitionMonth);

    /**
     * 根据时间范围和用户查询，优先扫描 JSONL 摘要，按需读取 Parquet。
     *
     * @param userId 用户ID
     * @param from   起始时间
     * @param to     结束时间
     * @param limit  最大返回条数
     * @return 符合条件的记录列表
     */
    List<StoredMemoryEntry> queryByTimeRange(String userId, Instant from, Instant to, int limit);

    /**
     * 根据ID列表读取完整记录（从 Parquet）。
     */
    List<StoredMemoryEntry> readFullRecordsByIds(List<String> ids);

    /**
     * 对指定分区进行 Compaction，合并小文件。
     *
     * @param partitionMonth 分区月份
     * @return 合并后文件数量
     */
    int compact(String partitionMonth);

    /**
     * 列出所有分区。
     */
    List<String> listPartitions();

    /**
     * 将指定分区的数据迁移到冷存储，并返回迁移的记录数。
     */
    long archiveToCold(String partitionMonth, ColdStorage coldStorage);
}