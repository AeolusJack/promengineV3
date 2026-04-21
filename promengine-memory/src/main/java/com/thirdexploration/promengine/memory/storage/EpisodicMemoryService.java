package com.thirdexploration.promengine.memory.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.model.Provenance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpisodicMemoryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryMetadataRegistry registry;

    private static final String INSERT_SQL = """
            INSERT INTO episodic_memory
            (id, user_id, content, summary, timestamp, memory_type, importance,
             metadata, ttl_seconds, domain, project_id, strength, layer,
             utility_score, safety_score, sharing_level, provenance, retrieval_count, session_id,created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)
            """;

    private static final String SELECT_BY_TIME_RANGE = """
            SELECT * FROM episodic_memory
            WHERE user_id = ? AND domain = ? AND timestamp BETWEEN ? AND ?
            AND deleted = 0
            """;

    private static final String SELECT_BY_ID = """
            SELECT * FROM episodic_memory WHERE id = ? AND deleted = 0
            """;

    private static final String SOFT_DELETE = """
            UPDATE episodic_memory SET deleted = 1, deleted_at = ? WHERE id = ?
            """;

    private static final String HARD_DELETE = "DELETE FROM episodic_memory WHERE id = ?";

    private static final String UPDATE_STRENGTH = """
            UPDATE episodic_memory SET strength = ? WHERE id = ?
            """;

    private static final String FIND_EXPIRED = """
            SELECT id FROM episodic_memory
            WHERE deleted = 0 AND ttl_seconds IS NOT NULL
            AND (timestamp + (ttl_seconds * 1000)) < ?
            LIMIT 1000
            """;

    // 统计 SQL
    private static final String COUNT_ALL = "SELECT COUNT(*) FROM episodic_memory WHERE deleted = 0";
    private static final String COUNT_BY_USER = "SELECT COUNT(*) FROM episodic_memory WHERE user_id = ? AND deleted = 0";
    private static final String COUNT_BY_SESSION = "SELECT COUNT(*) FROM episodic_memory WHERE session_id = ? AND deleted = 0";

    @Transactional
    public void store(MemoryRecord record) {
        if (record.getId() == null) record.setId(generateId());
        record.setLayer("episodic");
        if (record.getTtlSeconds() == null) {
            Duration ttl = registry.getLayerTTL("episodic");
            if (ttl != null) record.setTtlSeconds(ttl.toSeconds());
        }
        if (record.getDomain() == null) record.setDomain(registry.getDefaultDomain());
        if (record.getSharingLevel() == null) record.setSharingLevel(registry.getDefaultSharingLevel());

        try {
            jdbcTemplate.update(INSERT_SQL,
                    record.getId(),
                    record.getUserId(),
                    record.getContent(),
                    record.getSummary(),
                    record.getTimestamp().toEpochMilli(),
                    record.getMemoryType(),
                    record.getImportance(),
                    objectMapper.writeValueAsString(record.getMetadata()),
                    record.getTtlSeconds(),
                    record.getDomain(),
                    record.getProjectId(),
                    record.getStrength(),
                    record.getLayer(),
                    record.getUtilityScore(),
                    record.getSafetyScore(),
                    record.getSharingLevel(),
                    objectMapper.writeValueAsString(record.getProvenance()),
                    record.getRetrievalCount(),
                    record.getSessionId(),
                    System.currentTimeMillis()   // created_at
            );
            log.debug("Stored episodic memory: id={}, session={}", record.getId(), record.getSessionId());
        } catch (Exception e) {
            log.error("Failed to store episodic memory: id={}", record.getId(), e);
            throw new RuntimeException("Episodic memory store failed", e);
        }
    }

    @Transactional
    public void batchStore(List<MemoryRecord> records) {
        List<Object[]> batchArgs = new ArrayList<>();
        for (MemoryRecord rec : records) {
            if (rec.getId() == null) rec.setId(generateId());
            rec.setLayer("episodic");
            if (rec.getTtlSeconds() == null) {
                Duration ttl = registry.getLayerTTL("episodic");
                if (ttl != null) rec.setTtlSeconds(ttl.toSeconds());
            }
            if (rec.getDomain() == null) rec.setDomain(registry.getDefaultDomain());
            if (rec.getSharingLevel() == null) rec.setSharingLevel(registry.getDefaultSharingLevel());

            try {
                batchArgs.add(new Object[]{
                        rec.getId(), rec.getUserId(), rec.getContent(), rec.getSummary(),
                        rec.getTimestamp().toEpochMilli(), rec.getMemoryType(), rec.getImportance(),
                        objectMapper.writeValueAsString(rec.getMetadata()), rec.getTtlSeconds(),
                        rec.getDomain(), rec.getProjectId(), rec.getStrength(), rec.getLayer(),
                        rec.getUtilityScore(), rec.getSafetyScore(), rec.getSharingLevel(),
                        objectMapper.writeValueAsString(rec.getProvenance()), rec.getRetrievalCount(),
                        rec.getSessionId()
                });
            } catch (Exception e) {
                log.warn("Skipping record {} due to serialization error", rec.getId(), e);
            }
        }
        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_SQL, batchArgs);
            log.info("Batch stored {} episodic memories", batchArgs.size());
        }
    }

    /**
     * 按时间范围查询，支持 sessionId 过滤（若提供）
     */
    public List<MemoryRecord> queryByTimeRange(String userId, String domain, String sessionId,
                                               Instant from, Instant to, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_BY_TIME_RANGE);
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.add(domain);
        params.add(from.toEpochMilli());
        params.add(to.toEpochMilli());

        if (sessionId != null && !sessionId.isEmpty()) {
            sql.append(" AND session_id = ?");
            params.add(sessionId);
        }
        sql.append(" ORDER BY strength DESC, timestamp DESC LIMIT ?");
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), new EpisodicRowMapper(objectMapper), params.toArray());
    }

    /**
     * 兼容旧接口（无 sessionId）
     */
    public List<MemoryRecord> queryByTimeRange(String userId, String domain, Instant from, Instant to, int limit) {
        return queryByTimeRange(userId, domain, null, from, to, limit);
    }

    public MemoryRecord findById(String id) {
        try {
            return jdbcTemplate.queryForObject(SELECT_BY_ID, new EpisodicRowMapper(objectMapper), id);
        } catch (DataAccessException e) {
            return null;
        }
    }

    public void softDelete(String id) {
        jdbcTemplate.update(SOFT_DELETE, System.currentTimeMillis(), id);
    }

    public void hardDelete(String id) {
        jdbcTemplate.update(HARD_DELETE, id);
    }

    public void updateStrength(String id, float newStrength) {
        jdbcTemplate.update(UPDATE_STRENGTH, newStrength, id);
    }

    public List<String> getAllActiveIds() {
        String sql = "SELECT id FROM episodic_memory WHERE deleted = 0";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    public int cleanupExpired() {
        long now = System.currentTimeMillis();
        List<String> expiredIds = jdbcTemplate.queryForList(FIND_EXPIRED, String.class, now);
        for (String id : expiredIds) hardDelete(id);
        return expiredIds.size();
    }

    // ---------- 统计方法 ----------
    public long countAll() {
        Long count = jdbcTemplate.queryForObject(COUNT_ALL, Long.class);
        return count != null ? count : 0L;
    }

    public long countByUser(String userId) {
        Long count = jdbcTemplate.queryForObject(COUNT_BY_USER, Long.class, userId);
        return count != null ? count : 0L;
    }

    public long countBySession(String sessionId) {
        Long count = jdbcTemplate.queryForObject(COUNT_BY_SESSION, Long.class, sessionId);
        return count != null ? count : 0L;
    }

    private String generateId() {
        return "ep_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static class EpisodicRowMapper implements RowMapper<MemoryRecord> {
        private final ObjectMapper objectMapper;
        EpisodicRowMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

        @Override
        public MemoryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                Map<String, Object> metadata = objectMapper.readValue(
                        rs.getString("metadata"), new TypeReference<Map<String, Object>>() {});
                Provenance provenance = rs.getString("provenance") != null
                        ? objectMapper.readValue(rs.getString("provenance"), Provenance.class) : null;

                return MemoryRecord.builder()
                        .id(rs.getString("id"))
                        .userId(rs.getString("user_id"))
                        .content(rs.getString("content"))
                        .summary(rs.getString("summary"))
                        .timestamp(Instant.ofEpochMilli(rs.getLong("timestamp")))
                        .memoryType(rs.getString("memory_type"))
                        .importance(rs.getFloat("importance"))
                        .metadata(metadata)
                        .ttlSeconds(rs.getObject("ttl_seconds", Long.class))
                        .domain(rs.getString("domain"))
                        .projectId(rs.getString("project_id"))
                        .strength(rs.getFloat("strength"))
                        .layer(rs.getString("layer"))
                        .utilityScore(rs.getDouble("utility_score"))
                        .safetyScore(rs.getDouble("safety_score"))
                        .sharingLevel(rs.getString("sharing_level"))
                        .provenance(provenance)
                        .retrievalCount(rs.getInt("retrieval_count"))
                        .sessionId(rs.getString("session_id"))
                        .build();
            } catch (Exception e) {
                throw new SQLException("Failed to map episodic memory row", e);
            }
        }
    }
}