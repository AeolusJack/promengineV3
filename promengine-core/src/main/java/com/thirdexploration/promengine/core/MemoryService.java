package com.thirdexploration.promengine.core;

import com.thirdexploration.promengine.core.domain.MemoryEntry;
import com.thirdexploration.promengine.core.domain.Query;
import com.thirdexploration.promengine.core.domain.RetrievalStrategy;
import com.thirdexploration.promengine.core.domain.SearchResult;

/**
 * 统一记忆服务接口，抽象了分层存储、检索与遗忘。
 */
public interface MemoryService {

    /**
     * 存储一条记忆。
     *
     * @param entry 记忆条目
     */
    void store(MemoryEntry entry);

    /**
     * 根据查询条件检索记忆。
     *
     * @param query    查询内容
     * @param strategy 检索策略（时间窗口、是否扫描冷存储等）
     * @return 检索结果
     */
    SearchResult retrieve(Query query, RetrievalStrategy strategy);

    /**
     * 遗忘特定记忆（软删除或硬删除）。
     *
     * @param memoryId  记忆ID
     * @param permanent true 立即硬删除；false 进入软删除过渡期
     */
    void forget(String memoryId, boolean permanent);

    /**
     * 触发记忆反思与归纳（后台任务）。
     */
    void reflect();

    /**
     * 获取记忆系统预热状态。
     *
     * @return 预热状态对象
     */
    WarmupStatus getWarmupStatus();

    /**
     * 预热状态描述。
     */
    interface WarmupStatus {
        boolean isComplete();
        double getProgress();
        String getCurrentPhase();
    }
}