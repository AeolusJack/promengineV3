package com.thirdexploration.promengine.memory.model;

import lombok.Builder;
import lombok.Data;

/**
 * aeon
 * 记忆系统统计信息，用于监控和运维。
 */
@Data
@Builder
public class MemoryStats {

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 记忆域
     */
    private String domain;

    /**
     * 各层级记录数量
     */
    private java.util.Map<String, Long> layerCounts;

    /**
     * 总记录数
     */
    private long totalRecords;

    /**
     * 总存储大小（字节）
     */
    private long totalStorageBytes;

    /**
     * 平均记忆强度
     */
    private double averageStrength;

    /**
     * 今日新增记录数
     */
    private long todayNewRecords;

    /**
     * 今日检索次数
     */
    private long todayRetrievalCount;
}