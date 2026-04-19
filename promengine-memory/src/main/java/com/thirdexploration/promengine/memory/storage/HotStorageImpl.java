package com.thirdexploration.promengine.memory.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.domain.MemoryEntry;
import com.thirdexploration.promengine.memory.config.MemoryProperties;
import com.thirdexploration.promengine.memory.exception.MemoryStorageException;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.model.StoredMemoryEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 热存储 SQLite 实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotStorageImpl implements HotStorage {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryProperties properties;

    private static final String INSERT_SQL = """
            INSERT INTO hot_memory (id, user_id, content, summary, timestamp, memory_type,
                                    importance, metadata, ttl_seconds, deleted, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT * FROM hot_memory WHERE id = ? AND deleted = 0
            """;

    private static final String SELECT_BY_TIME_RANGE = """
            SELECT * FROM hot_memory
            WHERE user_id = ? AND timestamp BETWEEN ? AND ? AND deleted = 0
            ORDER BY timestamp DESC LIMIT ?
            """;

    private static final String SEARCH_KEYWORD = """
            SELECT * FROM hot_memory
            WHERE user_id = ? AND deleted = 0 AND (content LIKE ? OR summary LIKE ?)
            ORDER BY timestamp DESC LIMIT ?
            """;

    private static final String SOFT_DELETE = """
            UPDATE hot_memory SET deleted = 1, deleted_at = ? WHERE id = ?
            """;

    private static final String HARD_DELETE = "DELETE FROM hot_memory WHERE id = ?";

    private static final String FIND_EXPIRED = """
            SELECT * FROM hot_memory
            WHERE timestamp < ? AND deleted = 0
            ORDER BY timestamp ASC LIMIT 1000
            """;

    @Override
    public long countActive() {
        String sql = "SELECT COUNT(*) FROM hot_memory WHERE deleted = 0";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }
    @Override
    public void insert(MemoryRecord record) {
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
                    0,  // deleted false
                    System.currentTimeMillis()
            );
            log.debug("Inserted memory record id={} into hot storage", record.getId());
        } catch (Exception e) {
            log.error("Failed to insert memory record id={}", record.getId(), e);
            throw new MemoryStorageException("Hot storage insert failed", e);
        }
    }

    @Override
    @Transactional
    public void batchInsert(List<MemoryRecord> records) {
        List<Object[]> batchArgs = new ArrayList<>();
        for (MemoryRecord rec : records) {
            try {
                batchArgs.add(new Object[]{
                        rec.getId(),
                        rec.getUserId(),
                        rec.getContent(),
                        rec.getSummary(),
                        rec.getTimestamp().toEpochMilli(),
                        rec.getMemoryType(),
                        rec.getImportance(),
                        objectMapper.writeValueAsString(rec.getMetadata()),
                        rec.getTtlSeconds(),
                        0,
                        System.currentTimeMillis()
                });
            } catch (Exception e) {
                log.warn("Skipping record {} due to serialization error", rec.getId(), e);
            }
        }
        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_SQL, batchArgs);
            log.info("Batch inserted {} records into hot storage", batchArgs.size());
        }
    }

    @Override
    public StoredMemoryEntry findById(String id) {
        try {
            return jdbcTemplate.queryForObject(SELECT_BY_ID, new MemoryRowMapper(), id);
        } catch (DataAccessException e) {
            log.debug("Memory record id={} not found", id);
            return null;
        }
    }

    @Override
    public List<StoredMemoryEntry> findByTimeRange(String userId, Instant from, Instant to, int limit) {
        return jdbcTemplate.query(SELECT_BY_TIME_RANGE,
                new MemoryRowMapper(),
                userId, from.toEpochMilli(), to.toEpochMilli(), limit);
    }

    @Override
    public List<StoredMemoryEntry> searchByKeyword(String userId, String keyword, int limit) {
        String likePattern = "%" + keyword + "%";
        return jdbcTemplate.query(SEARCH_KEYWORD,
                new MemoryRowMapper(),
                userId, likePattern, likePattern, limit);
    }

    @Override
    public void softDelete(String id) {
        jdbcTemplate.update(SOFT_DELETE, System.currentTimeMillis(), id);
        log.info("Soft deleted memory id={}", id);
    }

    @Override
    public void hardDelete(String id) {
        jdbcTemplate.update(HARD_DELETE, id);
        log.info("Hard deleted memory id={}", id);
    }

    @Override
    public List<StoredMemoryEntry> migrateToWarm(Instant beforeTimestamp) {
        List<StoredMemoryEntry> records = jdbcTemplate.query(FIND_EXPIRED,
                new MemoryRowMapper(), beforeTimestamp.toEpochMilli());
        if (!records.isEmpty()) {
            log.info("Found {} records to migrate to warm storage", records.size());
        }
        return records;
    }

    private class MemoryRowMapper implements RowMapper<StoredMemoryEntry> {
        @Override
        public StoredMemoryEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                Map<String, Object> metadata = objectMapper.readValue(
                        rs.getString("metadata"),
                        new TypeReference<Map<String, Object>>() {}
                );
                return StoredMemoryEntry.builder()
                        .id(rs.getString("id"))
                        .userId(rs.getString("user_id"))
                        .content(rs.getString("content"))
                        .summary(rs.getString("summary"))
                        .timestamp(Instant.ofEpochMilli(rs.getLong("timestamp")))
                        .type(MemoryEntry.MemoryType.valueOf(rs.getString("memory_type")))
                        .importance(rs.getFloat("importance"))
                        .metadata(metadata)
                        .ttlSeconds(rs.getObject("ttl_seconds", Long.class))
                        .storageTier("HOT")
                        .build();
            } catch (Exception e) {
                throw new SQLException("Failed to map row", e);
            }
        }
    }
}