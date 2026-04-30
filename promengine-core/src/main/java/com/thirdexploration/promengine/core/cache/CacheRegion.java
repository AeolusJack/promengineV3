package com.thirdexploration.promengine.core.cache;

/**
 * 预定义的缓存区域，不同区域拥有独立的配置和命名空间。
 */
public enum CacheRegion {
    /** Agent 状态缓存（碳基模式精力值等） */
    AGENT_STATE("agentState", 1000, 300),
    /** 群聊状态缓存 */
    GROUP_STATE("groupState", 500, 1200),
    /** 会话元数据缓存 */
    SESSION_META("sessionMeta", 2000, 600),
    /** 工具调用统计缓存 */
    TOOL_STATS("toolStats", 500, 120),
    /** 用户权限缓存 */
    USER_PERMISSIONS("userPermissions", 2000, 600),
    /** 通用临时缓存 */
    TEMP("temp", 1000, 60);

    private final String regionName;
    private final int maxSize;
    private final int ttlSeconds;

    CacheRegion(String regionName, int maxSize, int ttlSeconds) {
        this.regionName = regionName;
        this.maxSize = maxSize;
        this.ttlSeconds = ttlSeconds;
    }

    public String getRegionName() { return regionName; }
    public int getMaxSize() { return maxSize; }
    public int getTtlSeconds() { return ttlSeconds; }
}