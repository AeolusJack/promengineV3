package com.thirdexploration.promengine.core.cache;

/**
 * 预定义的缓存区域，不同区域拥有独立的配置和命名空间。
 */
public enum CacheRegion {
    AGENT_STATE("agentState", 1000, 300),
    GROUP_STATE("groupState", 500, 1200),
    SESSION_META("sessionMeta", 2000, 600),
    TOOL_STATS("toolStats", 500, 120),
    USER_PERMISSIONS("userPermissions", 2000, 600),
    TEMP("temp", 1000, 60),
    // 新增：流式片段缓存，最大 5000 个 executionId，每个 fragment 列表是 List<String>，TTL 1 小时
    STREAM_FRAGMENT("streamFragment", 5000, 3600);

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