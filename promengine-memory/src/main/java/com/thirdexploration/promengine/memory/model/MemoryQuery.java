package com.thirdexploration.promengine.memory.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 记忆查询条件，支持多域、多层、权限过滤。
 * 优化：增加时间范围字段、默认值安全处理。
 */
@Data
@Builder
public class MemoryQuery {

    /**
     * 查询文本（用于语义检索）
     */
    private String text;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 主查询域（单域查询时使用）
     */
    private String domain;

    /**
     * 多域查询列表
     */
    private List<String> domains;

    /**
     * 项目 ID（用于项目隔离）
     */
    private String projectId;

    /**
     * 指定记忆层级
     */
    private String layer;

    /**
     * 最低共享级别（权限过滤）
     */
    private String minSharingLevel;

    /**
     * 最大返回结果数
     */
    @Builder.Default
    private int maxResults = 10;

    /**
     * 最小记忆强度阈值
     */
    @Builder.Default
    private float minStrength = 0.0f;

    /**
     * 是否包含工作记忆
     */
    @Builder.Default
    private boolean includeWorking = true;

    /**
     * 是否包含情景记忆
     */
    @Builder.Default
    private boolean includeEpisodic = true;

    /**
     * 是否包含语义记忆
     */
    @Builder.Default
    private boolean includeSemantic = true;

    /**
     * 是否包含过程记忆
     */
    @Builder.Default
    private boolean includeProcedural = false;

    /**
     * 是否包含集体记忆
     */
    @Builder.Default
    private boolean includeCollective = false;

    /**
     * 起始时间（用于时间范围过滤）
     */
    private Instant fromTime;

    /**
     * 结束时间（用于时间范围过滤）
     */
    private Instant toTime;

    /**
     * 默认检索天数（当 fromTime 未设置时，自动向前推这么多天）
     */
    @Builder.Default
    private int defaultDays = 30;

    /**
     * 获取有效的主域（永不 null）
     */
    public String getEffectiveDomain() {
        if (domain != null && !domain.isBlank()) return domain;
        if (domains != null && !domains.isEmpty()) return domains.get(0);
        return "general";
    }

    /**
     * 判断是否跨域查询
     */
    public boolean isCrossDomain() {
        return domains != null && domains.size() > 1;
    }

    /**
     * 获取多域查询列表，永不返回 null
     */
    public List<String> getDomains() {
        return domains != null ? domains : List.of();
    }

    /**
     * 获取所有查询域，永不返回 null
     */
    public List<String> getAllDomains() {
        if (domains != null && !domains.isEmpty()) return domains;
        if (domain != null && !domain.isBlank()) return List.of(domain);
        return List.of("general");
    }

    /**
     * 获取起始时间（若未设置则自动根据 defaultDays 向前推算）
     */
    public Instant getFromTime() {
        if (fromTime != null) return fromTime;
        return Instant.now().minusSeconds(defaultDays * 86400L);
    }

    /**
     * 获取结束时间（若未设置则为当前时间）
     */
    public Instant getToTime() {
        return toTime != null ? toTime : Instant.now();
    }

    /**
     * 创建简单的文本查询
     */
    public static MemoryQuery textQuery(String userId, String text, int maxResults) {
        return MemoryQuery.builder()
                .userId(userId)
                .text(text)
                .maxResults(maxResults)
                .build();
    }

    /**
     * 创建域内查询
     */
    public static MemoryQuery domainQuery(String userId, String domain, String text, int maxResults) {
        return MemoryQuery.builder()
                .userId(userId)
                .domain(domain)
                .text(text)
                .maxResults(maxResults)
                .build();
    }
}