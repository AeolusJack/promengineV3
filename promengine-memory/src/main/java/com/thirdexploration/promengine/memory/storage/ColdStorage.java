package com.thirdexploration.promengine.memory.storage;

import com.thirdexploration.promengine.memory.model.StoredMemoryEntry;

import java.util.List;

/**
 * 冷存储接口，负责归档数据的长期保存。
 */
public interface ColdStorage {

    /**
     * 归档一批记录。
     *
     * @param records 待归档记录
     * @param archiveId 归档标识（如年份）
     */
    void archive(List<StoredMemoryEntry> records, String archiveId);

    /**
     * 从冷存储中检索记录（按ID列表，用于用户明确请求扫描冷数据）。
     *
     * @param ids 记录ID列表
     * @return 匹配的记录
     */
    List<StoredMemoryEntry> retrieveByIds(List<String> ids);

    /**
     * 列出所有归档ID。
     */
    List<String> listArchives();
}