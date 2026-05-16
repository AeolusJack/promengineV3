package com.thirdexploration.promengine.memory.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.model.Provenance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 情景记忆服务，存储用户历史对话和经验。
 * 优化：使用 NamedParameterJdbcTemplate 提升可读性，批量操作统一异常处理，
 * 查询方法增加只读事务，防止脏读。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EpisodicMemoryService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryMetadataRegistry registry;

    // ---------- SQL 语句（使用传统字符串，兼容所有 JDK 版本）----------
    private static final String INSERT_SQL = """
            INSERT INTO episodic_memory
            (id, user_id, content, summary, timestamp, memory_type, importance,
             metadata, ttl_seconds, domain, project_id, strength, layer,
             utility_score, safety_score, sharing_level, provenance, retrieval_count,
             session_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID = "SELECT * FROM episodic_memory WHERE id = :id AND deleted = 0";
    private static final String SELECT_BY_IDS = "SELECT * FROM episodic_memory WHERE id IN (:ids) AND deleted = 0";
    private static final String SELECT_BY_TIME_RANGE_BASE = """
            SELECT * FROM episodic_memory
            WHERE user_id = :userId AND domain = :domain
            AND timestamp BETWEEN :from AND :to AND deleted = 0
            """;
    private static final String SOFT_DELETE = "UPDATE episodic_memory SET deleted = 1, deleted_at = :now WHERE id = :id";
    private static final String UPDATE_STRENGTH = "UPDATE episodic_memory SET strength = :strength WHERE id = :id";
    private static final String UPDATE_SCORES = "UPDATE episodic_memory SET utility_score = :utility, safety_score = :safety WHERE id = :id";
    private static final String FIND_EXPIRED = """
            SELECT id FROM episodic_memory
            WHERE deleted = 0 AND ttl_seconds IS NOT NULL
            AND (timestamp + (ttl_seconds * 1000)) < :now
            LIMIT 1000
            """;
    private static final String COUNT_ALL = "SELECT COUNT(*) FROM episodic_memory WHERE deleted = 0";
    private static final String COUNT_BY_USER = "SELECT COUNT(*) FROM episodic_memory WHERE user_id = :userId AND deleted = 0";
    private static final String COUNT_BY_SESSION = "SELECT COUNT(*) FROM episodic_memory WHERE session_id = :sessionId AND deleted = 0";

    // ---------- 写操作 ----------

    @Transactional
    public void store(MemoryRecord record) {
        ensureDefaults(record);
        try {
            int affected = jdbcTemplate.update(INSERT_SQL,
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
                    System.currentTimeMillis()
            );
            if (affected != 1) {
                log.warn("Unexpected insert result: {} rows affected for id={}", affected, record.getId());
            }
            log.debug("Stored episodic memory: id={}, session={}", record.getId(), record.getSessionId());
        } catch (Exception e) {
            log.error("Failed to store episodic memory: id={}", record.getId(), e);
            throw new RuntimeException("Episodic memory store failed", e);
        }
    }

    @Transactional
    public void batchStore(List<MemoryRecord> records) {
        if (records == null || records.isEmpty()) return;
        List<Object[]> batchArgs = new ArrayList<>(records.size());
        for (MemoryRecord rec : records) {
            ensureDefaults(rec);
            try {
                batchArgs.add(new Object[]{
                        rec.getId(), rec.getUserId(), rec.getContent(), rec.getSummary(),
                        rec.getTimestamp().toEpochMilli(), rec.getMemoryType(), rec.getImportance(),
                        objectMapper.writeValueAsString(rec.getMetadata()), rec.getTtlSeconds(),
                        rec.getDomain(), rec.getProjectId(), rec.getStrength(), rec.getLayer(),
                        rec.getUtilityScore(), rec.getSafetyScore(), rec.getSharingLevel(),
                        objectMapper.writeValueAsString(rec.getProvenance()), rec.getRetrievalCount(),
                        rec.getSessionId(), System.currentTimeMillis()
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

    // 批量更新，使用 case when 或逐条更新，推荐使用 NamedParameterJdbcTemplate
    @Transactional
    public void batchUpdateStrengths(List<MemoryRecord> records) {
        String sql = "UPDATE episodic_memory SET strength = :strength WHERE id = :id";
        MapSqlParameterSource[] batch = records.stream()
                .map(r -> new MapSqlParameterSource()
                        .addValue("strength", r.getStrength())
                        .addValue("id", r.getId()))
                .toArray(MapSqlParameterSource[]::new);
        namedJdbcTemplate.batchUpdate(sql, batch);
    }
    // ---------- 查询操作（只读事务）----------

    @Transactional(readOnly = true)
    public List<MemoryRecord> queryByTimeRange(String userId, String domain, String sessionId,
                                               Instant from, Instant to, int limit, String projectId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("domain", domain)
                .addValue("from", from.toEpochMilli())
                .addValue("to", to.toEpochMilli());

        StringBuilder sqlBuilder = new StringBuilder(SELECT_BY_TIME_RANGE_BASE);
        if (sessionId != null && !sessionId.isBlank()) {
            sqlBuilder.append(" AND session_id = :sessionId");
            params.addValue("sessionId", sessionId);
        }
        if (projectId != null && !projectId.isBlank()) {
            sqlBuilder.append(" AND project_id = :projectId");
            params.addValue("projectId", projectId);
        }
        sqlBuilder.append(" ORDER BY strength DESC, timestamp DESC LIMIT :limit");
        params.addValue("limit", limit);

        return namedJdbcTemplate.query(sqlBuilder.toString(), params, new EpisodicRowMapper(objectMapper));
    }

    @Transactional(readOnly = true)
    public List<MemoryRecord> queryByTimeRange(String userId, String domain, Instant from, Instant to, int limit, String projectId) {
        return queryByTimeRange(userId, domain, null, from, to, limit, projectId);
    }

    @Transactional(readOnly = true)
    public MemoryRecord findById(String id) {
        try {
            MapSqlParameterSource params = new MapSqlParameterSource("id", id);
            return namedJdbcTemplate.queryForObject(SELECT_BY_ID, params, new EpisodicRowMapper(objectMapper));
        } catch (DataAccessException e) {
            log.debug("Memory not found by id: {}", id);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<MemoryRecord> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        return namedJdbcTemplate.query(SELECT_BY_IDS, params, new EpisodicRowMapper(objectMapper));
    }

    @Transactional(readOnly = true)
    public PageResult<MemoryEntry> findByKeywordAndPage(String keyword, int page, int size) {
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM episodic_memory WHERE deleted = 0");
        StringBuilder dataSql = new StringBuilder("SELECT * FROM episodic_memory WHERE deleted = 0");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword + "%";
            countSql.append(" AND (content LIKE ? OR summary LIKE ?)");
            dataSql.append(" AND (content LIKE ? OR summary LIKE ?)");
            params.add(like);
            params.add(like);
        }

        long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, params.toArray());
        int offset = (page - 1) * size;
        dataSql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);

        List<MemoryRecord> records = jdbcTemplate.query(dataSql.toString(), new EpisodicRowMapper(objectMapper), params.toArray());
        List<MemoryEntry> entries = records.stream().map(MemoryRecord::toMemoryEntry).collect(Collectors.toList());
        return new PageResult<>(entries, total);
    }

    // 在 EpisodicMemoryService 中添加
    public List<String> getAllActiveIdsPaginated(int page, int size) {
        int offset = page * size;
        String sql = "SELECT id FROM episodic_memory WHERE deleted = 0 ORDER BY id LIMIT ? OFFSET ?";
        return jdbcTemplate.queryForList(sql, String.class, size, offset);
    }
    // ---------- 统计与维护 ----------

    @Transactional(readOnly = true)
    public long countAll() {
        Long count = jdbcTemplate.queryForObject(COUNT_ALL, Long.class);
        return count != null ? count : 0L;
    }

    @Transactional(readOnly = true)
    public long countByUser(String userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        Long count = namedJdbcTemplate.queryForObject(COUNT_BY_USER, params, Long.class);
        return count != null ? count : 0L;
    }

    @Transactional(readOnly = true)
    public long countBySession(String sessionId) {
        MapSqlParameterSource params = new MapSqlParameterSource("sessionId", sessionId);
        Long count = namedJdbcTemplate.queryForObject(COUNT_BY_SESSION, params, Long.class);
        return count != null ? count : 0L;
    }

    @Transactional
    public int cleanupExpired() {
        long now = System.currentTimeMillis();
        MapSqlParameterSource params = new MapSqlParameterSource("now", now);
        List<String> expiredIds = namedJdbcTemplate.queryForList(FIND_EXPIRED, params, String.class);
        for (String id : expiredIds) {
            hardDelete(id);
        }
        return expiredIds.size();
    }

    @Transactional
    public void softDelete(String id) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", System.currentTimeMillis())
                .addValue("id", id);
        namedJdbcTemplate.update(SOFT_DELETE, params);
    }

    @Transactional
    public void hardDelete(String id) {
        jdbcTemplate.update("DELETE FROM episodic_memory WHERE id = ?", id);
    }

    @Transactional
    public void updateStrength(String id, float newStrength) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("strength", newStrength)
                .addValue("id", id);
        namedJdbcTemplate.update(UPDATE_STRENGTH, params);
    }

    @Transactional
    public void updateScores(String id, double utilityScore, double safetyScore) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("utility", utilityScore)
                .addValue("safety", safetyScore)
                .addValue("id", id);
        namedJdbcTemplate.update(UPDATE_SCORES, params);
    }

    // ---------- 私有辅助 ----------
    private void ensureDefaults(MemoryRecord record) {
        if (record.getId() == null) record.setId(generateId());
        if (record.getLayer() == null) record.setLayer("episodic");
        if (record.getTimestamp() == null) record.setTimestamp(Instant.now());
        if (record.getTtlSeconds() == null) {
            Duration ttl = registry.getLayerTTL("episodic");
            if (ttl != null) record.setTtlSeconds(ttl.toSeconds());
        }
        if (record.getDomain() == null) record.setDomain(registry.getDefaultDomain());
        if (record.getSharingLevel() == null) record.setSharingLevel(registry.getDefaultSharingLevel());
        if (record.getMetadata() == null) record.setMetadata(new HashMap<>());
        if (record.getProvenance() == null) {
            record.setProvenance(Provenance.userInput(record.getUserId()));
        }
    }

    private String generateId() {
        return "ep_" + UUID.randomUUID().toString().replace("-", "");
    }

    // ---------- RowMapper ----------
    private static class EpisodicRowMapper implements RowMapper<MemoryRecord> {
        private final ObjectMapper objectMapper;
        EpisodicRowMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

        @Override
        public MemoryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                Map<String, Object> metadata = objectMapper.readValue(
                        rs.getString("metadata"), new TypeReference<>() {});
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
                        .strength(rs.getDouble("strength"))
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

    // ---------- 内部结果封装 ----------
    public record PageResult<T>(List<T> data, long total) {}
}