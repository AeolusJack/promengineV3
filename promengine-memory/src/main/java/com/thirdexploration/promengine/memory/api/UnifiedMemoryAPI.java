package com.thirdexploration.promengine.memory.api;

import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryMetadata;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.memory.model.MemoryStats;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * aeon
 * 统一记忆 API，提供记忆的写入、检索、遗忘、统计等核心操作。
 * 所有记忆操作均通过此接口进行，确保一致性治理。
 */
public interface UnifiedMemoryAPI {

    /**
     * 写入一条记忆
     * @param content 记忆内容
     * @param metadata 元数据（包含域、层级、权限等）
     */
    void remember(String content, MemoryMetadata metadata);

    /**
     * 写入一条完整的记忆记录
     * @param entry 记忆条目
     */
    void remember(MemoryEntry entry);

    /**
     * 检索记忆
     * @param query 查询条件
     * @return 匹配的记忆列表
     */
    List<MemoryEntry> recall(MemoryQuery query);

    /**
     * 异步检索记忆
     * @param query 查询条件
     * @return 异步结果
     */
    CompletableFuture<List<MemoryEntry>> recallAsync(MemoryQuery query);

    /**
     * 遗忘指定记忆
     * @param memoryId 记忆 ID
     * @param permanent true 表示永久删除，false 表示软删除
     */
    void forget(String memoryId, boolean permanent);

    /**
     * 更新记忆强度
     * @param memoryId 记忆 ID
     * @param newStrength 新强度值
     */
    void updateStrength(String memoryId, float newStrength);

    /**
     * 获取记忆统计信息
     * @param userId 用户 ID
     * @param domain 记忆域
     * @return 统计信息
     */
    MemoryStats getStats(String userId, String domain);

    /**
     * 将工作记忆提升为情景记忆（通常由任务结束时触发）
     * @param sessionId 会话 ID
     */
    void promoteWorkingToEpisodic(String sessionId);

    /**
     * 执行记忆反思与蒸馏（后台任务触发）
     */
    void reflect();

    // UnifiedMemoryAPI.java
    default long count() {
        // 注意：getStats 需要 userId 和 domain，这里只能传 null 表示全局统计
        // 具体实现需在 UnifiedMemoryAPIImpl 中支持 null 参数
        MemoryStats stats = getStats(null, null);
        return stats != null ? stats.getTotalRecords() : 0L;
    }
}