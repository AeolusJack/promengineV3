package com.thirdexploration.promengine.memory.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.model.Provenance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * 集体记忆服务（L5）。
 * 跨 Agent 共享的记忆，支持按共享级别过滤。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectiveMemoryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryMetadataRegistry registry;

    private static final String INSERT_SQL = """
            INSERT INTO collective_memory
            (id, content, summary, timestamp, memory_type, importance,
             metadata, domain, project_id, strength, layer, utility_score,
             safety_score, sharing_level, provenance, retrieval_count, owner_id,created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)
            """;

    private static final String SELECT_SHARED = """
            SELECT * FROM collective_memory
            WHERE domain = ? AND sharing_level IN (?, ?, ?) AND deleted = 0
            ORDER BY utility_score DESC, timestamp DESC LIMIT ?
            """;

    private static final String SOFT_DELETE = """
            UPDATE collective_memory SET deleted = 1 WHERE id = ?
            """;

    private static final String HARD_DELETE = "DELETE FROM collective_memory WHERE id = ?";

    // 统计 SQL
    private static final String COUNT_ALL = "SELECT COUNT(*) FROM collective_memory WHERE deleted = 0";

    @Transactional
    public void store(MemoryRecord record) {
        if (record.getId() == null) record.setId(generateId());
        record.setLayer("collective");
        if (record.getDomain() == null) record.setDomain(registry.getDefaultDomain());
        if (record.getSharingLevel() == null) record.setSharingLevel("domain");

        try {
            jdbcTemplate.update(INSERT_SQL,
                    record.getId(),
                    record.getContent(),
                    record.getSummary(),
                    record.getTimestamp().toEpochMilli(),
                    record.getMemoryType(),
                    record.getImportance(),
                    objectMapper.writeValueAsString(record.getMetadata()),
                    record.getDomain(),
                    record.getProjectId(),
                    record.getStrength(),
                    record.getLayer(),
                    record.getUtilityScore(),
                    record.getSafetyScore(),
                    record.getSharingLevel(),
                    objectMapper.writeValueAsString(record.getProvenance()),
                    record.getRetrievalCount(),
                    record.getUserId(),
                    System.currentTimeMillis()   // created_at
            );
            log.debug("Stored collective memory: id={}, sharingLevel={}", record.getId(), record.getSharingLevel());
        } catch (Exception e) {
            log.error("Failed to store collective memory: id={}", record.getId(), e);
            throw new RuntimeException("Collective memory store failed", e);
        }
    }

    public List<MemoryRecord> queryShared(String domain, String requesterSharingLevel, int limit) {
        List<String> allowedLevels = switch (requesterSharingLevel) {
            case "global" -> List.of("private", "domain", "global");
            case "domain" -> List.of("private", "domain");
            default -> List.of("private");
        };
        return jdbcTemplate.query(SELECT_SHARED,
                new CollectiveRowMapper(objectMapper),
                domain, allowedLevels.get(0), allowedLevels.get(1), allowedLevels.get(2), limit);
    }

    public void softDelete(String id) {
        jdbcTemplate.update(SOFT_DELETE, id);
    }

    public void hardDelete(String id) {
        jdbcTemplate.update(HARD_DELETE, id);
    }

    // ---------- 统计方法 ----------
    public long countAll() {
        Long count = jdbcTemplate.queryForObject(COUNT_ALL, Long.class);
        return count != null ? count : 0L;
    }

    private String generateId() {
        return "coll_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static class CollectiveRowMapper implements RowMapper<MemoryRecord> {
        private final ObjectMapper objectMapper;
        CollectiveRowMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

        @Override
        public MemoryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                Map<String, Object> metadata = objectMapper.readValue(
                        rs.getString("metadata"), new TypeReference<Map<String, Object>>() {});
                Provenance provenance = rs.getString("provenance") != null
                        ? objectMapper.readValue(rs.getString("provenance"), Provenance.class) : null;

                return MemoryRecord.builder()
                        .id(rs.getString("id"))
                        .userId(rs.getString("owner_id"))
                        .content(rs.getString("content"))
                        .summary(rs.getString("summary"))
                        .timestamp(Instant.ofEpochMilli(rs.getLong("timestamp")))
                        .memoryType(rs.getString("memory_type"))
                        .importance(rs.getFloat("importance"))
                        .metadata(metadata)
                        .domain(rs.getString("domain"))
                        .projectId(rs.getString("project_id"))
                        .strength(rs.getFloat("strength"))
                        .layer(rs.getString("layer"))
                        .utilityScore(rs.getDouble("utility_score"))
                        .safetyScore(rs.getDouble("safety_score"))
                        .sharingLevel(rs.getString("sharing_level"))
                        .provenance(provenance)
                        .retrievalCount(rs.getInt("retrieval_count"))
                        .build();
            } catch (Exception e) {
                throw new SQLException("Failed to map collective memory row", e);
            }
        }
    }
}