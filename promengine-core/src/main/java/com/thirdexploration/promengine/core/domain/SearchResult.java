package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 检索结果。
 */
@Data
@Builder
public class SearchResult {
    private List<MemoryHit> hits;
    private long totalHits;
    private long tookMs;

    @Data
    @Builder
    public static class MemoryHit {
        private String memoryId;
        private String content;
        private float score;
        private Instant timestamp;
    }
}