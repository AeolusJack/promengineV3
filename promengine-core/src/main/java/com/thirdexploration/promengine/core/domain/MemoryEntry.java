package com.thirdexploration.promengine.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEntry {
    private String id;
    private String userId;
    private String content;
    private Instant timestamp;
    private MemoryType type;
    private float importance;
    private Map<String, Object> metadata;
    private Long ttlSeconds;

    public enum MemoryType {
        EPISODIC, SEMANTIC, PROCEDURAL
    }
}