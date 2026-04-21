package com.thirdexploration.promengine.memory.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.config.MetaPolicyStore;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * aeon
 * 过程记忆服务（L4）。
 * 存储可复用的执行过程、工具调用模板等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProceduralMemoryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryMetadataRegistry registry;

    private static final String INSERT_SQL = """
            INSERT INTO procedural_memory
            (id, user_id, content, summary, timestamp, memory_type, importance,
             metadata, domain, project_id, strength, layer, utility_score,
             safety_score, sharing_level, provenance, retrieval_count, 
             trigger_condition, reliability, session_id,created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)
            """;

    private static final String SELECT_BY_TRIGGER = """
            SELECT * FROM procedural_memory
            WHERE user_id = ? AND domain = ? AND trigger_condition = ?
            AND deleted = 0 AND reliability >= ?
            ORDER BY reliability DESC, utility_score DESC LIMIT ?
            """;

    private static final String SELECT_BY_ID = """
            SELECT * FROM procedural_memory WHERE id = ? AND deleted = 0
            """;

    private static final String UPDATE_RELIABILITY = """
            UPDATE procedural_memory SET reliability = ?, retrieval_count = retrieval_count + 1 WHERE id = ?
            """;

    private static final String SOFT_DELETE = """
            UPDATE procedural_memory SET deleted = 1 WHERE id = ?
            """;

    private static final String HARD_DELETE = "DELETE FROM procedural_memory WHERE id = ?";

    // 统计 SQL
    private static final String COUNT_ALL = "SELECT COUNT(*) FROM procedural_memory WHERE deleted = 0";
    private static final String COUNT_BY_USER = "SELECT COUNT(*) FROM procedural_memory WHERE user_id = ? AND deleted = 0";

    @Transactional
    public void store(MemoryRecord record) {
        if (record.getId() == null) record.setId(generateId());
        record.setLayer("procedural");
        if (record.getDomain() == null) record.setDomain(registry.getDefaultDomain());
        if (record.getSharingLevel() == null) record.setSharingLevel(registry.getDefaultSharingLevel());

        String triggerCondition = (String) record.getMetadata().getOrDefault("trigger", "");
        double reliability = record.getUtilityScore();

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
                    record.getDomain(),
                    record.getProjectId(),
                    record.getStrength(),
                    record.getLayer(),
                    record.getUtilityScore(),
                    record.getSafetyScore(),
                    record.getSharingLevel(),
                    objectMapper.writeValueAsString(record.getProvenance()),
                    record.getRetrievalCount(),
                    triggerCondition,
                    reliability,
                    record.getSessionId()   // 仅记录，不用于检索过滤
                    ,
                    System.currentTimeMillis()   // created_at
            );
            log.debug("Stored procedural memory: id={}, session={}", record.getId(), record.getSessionId());
        } catch (Exception e) {
            log.error("Failed to store procedural memory: id={}", record.getId(), e);
            throw new RuntimeException("Procedural memory store failed", e);
        }
    }

    public List<MemoryRecord> findByTrigger(String userId, String domain, String triggerCondition, int limit) {
        // 从注册表获取过程记忆层的配置，若不存在则使用默认可靠性阈值 0.7
        double minReliability = 0.7;
        MetaPolicyStore.MetaPolicyData.LayerDef layerConfig = registry.getLayerConfig("procedural");
        if (layerConfig != null) {
            // 此处我们期望过程记忆层有一个专门的可靠性阈值字段，若无，可暂时使用 forgettingRate 作为阈值
            minReliability = layerConfig.getForgettingRate();

//            minReliability = layerConfig.getReliabilityThreshold() > 0
//                    ? layerConfig.getReliabilityThreshold()
//                    : layerConfig.getForgettingRate();
        }
        return jdbcTemplate.query(SELECT_BY_TRIGGER,
                new ProceduralRowMapper(objectMapper),
                userId, domain, triggerCondition, minReliability, limit);
    }

    public MemoryRecord findById(String id) {
        try {
            return jdbcTemplate.queryForObject(SELECT_BY_ID, new ProceduralRowMapper(objectMapper), id);
        } catch (Exception e) {
            return null;
        }
    }

    public void boostReliability(String id, double increment) {
        MemoryRecord record = findById(id);
        if (record != null) {
            double newReliability = Math.min(1.0, record.getUtilityScore() + increment);
            jdbcTemplate.update(UPDATE_RELIABILITY, newReliability, id);
            log.debug("Boosted procedural memory reliability: id={}, newReliability={}", id, newReliability);
        }
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

    public long countByUser(String userId) {
        Long count = jdbcTemplate.queryForObject(COUNT_BY_USER, Long.class, userId);
        return count != null ? count : 0L;
    }

    private String generateId() {
        return "proc_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static class ProceduralRowMapper implements RowMapper<MemoryRecord> {
        private final ObjectMapper objectMapper;
        ProceduralRowMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

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
                throw new SQLException("Failed to map procedural memory row", e);
            }
        }
    }
}