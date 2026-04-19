package com.thirdexploration.promengine.memory.model;

import com.thirdexploration.promengine.core.domain.MemoryEntry;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 存储层内部使用的记忆条目，增加存储位置等元信息。
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StoredMemoryEntry extends MemoryEntry {
    private String summary;
    private String storageTier;      // HOT, WARM, COLD
    private String vectorId;         // 向量库中的ID
    private String parquetPath;      // 温/冷存储文件路径
    private long rowGroupIndex;      // Parquet 行组索引
}