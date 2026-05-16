package com.thirdexploration.promengine.memory.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.model.Provenance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 语义记忆服务，存储抽象化、概念化知识。
 * 优化：使用 NamedParameterJdbcTemplate，支持分页查询、批量更新、完善的空值处理。
 */
@Slf4j
@Service
public class SemanticMemoryService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryMetadataRegistry registry;
    private final VectorStorage vectorStorage;

    @Autowired(required = false)
    private Neo4jGraphService graphService; // 可选依赖

    // 构造器注入（必需依赖）
    public SemanticMemoryService(JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper,
                                 MemoryMetadataRegistry registry,
                                 VectorStorage vectorStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.vectorStorage = vectorStorage;
    }

    // ---------- SQL 常量 ----------
    private static final String INSERT_SQL = """
            INSERT INTO semantic_memory
            (id, user_id, content, summary, timestamp, memory_type, importance,
             metadata, domain, project_id, strength, layer, utility_score,
             safety_score, sharing_level, provenance, retrieval_count, vector_id,
             session_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID = "SELECT * FROM semantic_memory WHERE id = :id AND deleted = 0";
    private static final String SELECT_BY_IDS = "SELECT * FROM semantic_memory WHERE id IN (:ids) AND deleted = 0";
    private static final String UPDATE_STRENGTH = "UPDATE semantic_memory SET strength = :strength WHERE id = :id";
    private static final String UPDATE_SCORES = "UPDATE semantic_memory SET utility_score = :utility, safety_score = :safety WHERE id = :id";
    private static final String SOFT_DELETE = "UPDATE semantic_memory SET deleted = 1, deleted_at = :now WHERE id = :id";
    private static final String HARD_DELETE = "DELETE FROM semantic_memory WHERE id = :id";
    private static final String COUNT_ALL = "SELECT COUNT(*) FROM semantic_memory WHERE deleted = 0";
    private static final String COUNT_BY_USER = "SELECT COUNT(*) FROM semantic_memory WHERE user_id = :userId AND deleted = 0";
    private static final String GET_ALL_IDS = "SELECT id FROM semantic_memory WHERE deleted = 0 ORDER BY id LIMIT :limit OFFSET :offset";

    // ---------- 语义检索 ----------

    @Transactional(readOnly = true)
    public List<MemoryRecord> semanticSearch(String queryText, int topK) {
        if (vectorStorage == null || queryText == null || queryText.isBlank()) {
            return List.of();
        }
        List<VectorStorage.SearchHit> hits = vectorStorage.searchByText(queryText, topK);
        if (hits.isEmpty()) return List.of();
        List<String> ids = hits.stream().map(VectorStorage.SearchHit::id).collect(Collectors.toList());
        return findByIds(ids);
    }

    @Transactional(readOnly = true)
    public List<MemoryRecord> semanticSearch(float[] queryVector, int topK) {
        if (vectorStorage == null || queryVector == null || queryVector.length == 0) {
            return List.of();
        }
        List<VectorStorage.SearchHit> hits = vectorStorage.search(queryVector, topK);
        if (hits.isEmpty()) return List.of();
        List<String> ids = hits.stream().map(VectorStorage.SearchHit::id).collect(Collectors.toList());
        return findByIds(ids);
    }

    // ---------- 写操作 ----------

    @Transactional
    public void store(MemoryRecord record) {
        ensureDefaults(record);
        try {
            String vectorId = null;
            if (vectorStorage != null && record.getVector() != null && record.getVector().length > 0) {
                vectorId = record.getId();
                vectorStorage.add(vectorId, record.getVector(), objectMapper.writeValueAsString(record.getMetadata()));
            }
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
                    vectorId,
                    record.getSessionId(),
                    System.currentTimeMillis()
            );
            if (graphService != null) graphService.upsertMemoryNode(record);
            log.debug("Stored semantic memory: id={}", record.getId());
        } catch (Exception e) {
            log.error("Failed to store semantic memory: id={}", record.getId(), e);
            throw new RuntimeException("Semantic memory store failed", e);
        }
    }

    @Transactional
    public void batchStore(List<MemoryRecord> records) {
        if (CollectionUtils.isEmpty(records)) return;
        List<Object[]> batchArgs = new ArrayList<>(records.size());
        for (MemoryRecord rec : records) {
            ensureDefaults(rec);
            try {
                String vectorId = null;
                if (vectorStorage != null && rec.getVector() != null && rec.getVector().length > 0) {
                    vectorId = rec.getId();
                    vectorStorage.add(vectorId, rec.getVector(), objectMapper.writeValueAsString(rec.getMetadata()));
                }
                batchArgs.add(new Object[]{
                        rec.getId(), rec.getUserId(), rec.getContent(), rec.getSummary(),
                        rec.getTimestamp().toEpochMilli(), rec.getMemoryType(), rec.getImportance(),
                        objectMapper.writeValueAsString(rec.getMetadata()), rec.getDomain(),
                        rec.getProjectId(), rec.getStrength(), rec.getLayer(),
                        rec.getUtilityScore(), rec.getSafetyScore(), rec.getSharingLevel(),
                        objectMapper.writeValueAsString(rec.getProvenance()), rec.getRetrievalCount(),
                        vectorId, rec.getSessionId(), System.currentTimeMillis()
                });
            } catch (Exception e) {
                log.warn("Skipping record {} due to serialization error", rec.getId(), e);
            }
        }
        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_SQL, batchArgs);
            log.info("Batch stored {} semantic memories", batchArgs.size());
        }
    }

    // ---------- 批量更新强度（供 ForgettingCurveDecayer 使用）----------
    @Transactional
    public void batchUpdateStrengths(List<MemoryRecord> records) {
        if (CollectionUtils.isEmpty(records)) return;
        String sql = "UPDATE semantic_memory SET strength = :strength WHERE id = :id";
        MapSqlParameterSource[] batch = records.stream()
                .map(r -> new MapSqlParameterSource()
                        .addValue("strength", r.getStrength())
                        .addValue("id", r.getId()))
                .toArray(MapSqlParameterSource[]::new);
        namedJdbcTemplate.batchUpdate(sql, batch);
        log.debug("Batch updated strengths for {} semantic memories", records.size());
    }

    // ---------- 查询操作 ----------

    @Transactional(readOnly = true)
    public MemoryRecord findById(String id) {
        try {
            MapSqlParameterSource params = new MapSqlParameterSource("id", id);
            return namedJdbcTemplate.queryForObject(SELECT_BY_ID, params, new SemanticRowMapper(objectMapper));
        } catch (DataAccessException e) {
            log.debug("Semantic memory not found by id: {}", id);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<MemoryRecord> findByIds(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) return List.of();
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        return namedJdbcTemplate.query(SELECT_BY_IDS, params, new SemanticRowMapper(objectMapper));
    }

    /**
     * 分页获取所有活跃 ID（供遗忘曲线衰减器使用）
     */
    @Transactional(readOnly = true)
    public List<String> getAllIdsPaginated(int page, int size) {
        int offset = page * size;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", offset);
        return namedJdbcTemplate.queryForList(GET_ALL_IDS, params, String.class);
    }

    /**
     * 获取所有 ID（不推荐大表使用，建议使用分页版本）
     */
    @Transactional(readOnly = true)
    public List<String> getAllIds() {
        String sql = "SELECT id FROM semantic_memory WHERE deleted = 0";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    @Transactional(readOnly = true)
    public PageResult<MemoryEntry> findByKeywordAndPage(String keyword, int page, int size) {
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM semantic_memory WHERE deleted = 0");
        StringBuilder dataSql = new StringBuilder("SELECT * FROM semantic_memory WHERE deleted = 0");
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

        List<MemoryRecord> records = jdbcTemplate.query(dataSql.toString(), new SemanticRowMapper(objectMapper), params.toArray());
        List<MemoryEntry> entries = records.stream()
                .map(r -> MemoryEntry.builder()
                        .id(r.getId())
                        .userId(r.getUserId())
                        .content(r.getContent())
                        .summary(r.getSummary())
                        .timestamp(r.getTimestamp())
                        .memoryType(r.getMemoryType())
                        .importance(r.getImportance())
                        .domain(r.getDomain())
                        .layer(r.getLayer())
                        .strength(r.getStrength())
                        .sharingLevel(r.getSharingLevel())
                        .metadata(r.getMetadata())
                        .build())
                .collect(Collectors.toList());
        return new PageResult<>(entries, total);
    }

    // ---------- 更新与删除 ----------

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

    @Transactional
    public void softDelete(String id) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", System.currentTimeMillis())
                .addValue("id", id);
        namedJdbcTemplate.update(SOFT_DELETE, params);
        if (vectorStorage != null) vectorStorage.delete(id);
        if (graphService != null) graphService.deleteMemoryNode(id);
    }

    @Transactional
    public void hardDelete(String id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        namedJdbcTemplate.update(HARD_DELETE, params);
        if (vectorStorage != null) vectorStorage.delete(id);
        if (graphService != null) graphService.deleteMemoryNode(id);
    }

    // ---------- 统计 ----------

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

    // ---------- 私有辅助 ----------

    private void ensureDefaults(MemoryRecord record) {
        if (record.getId() == null) record.setId(generateId());
        if (record.getLayer() == null) record.setLayer("semantic");
        if (record.getTimestamp() == null) record.setTimestamp(Instant.now());
        if (record.getDomain() == null) record.setDomain(registry.getDefaultDomain());
        if (record.getSharingLevel() == null) record.setSharingLevel(registry.getDefaultSharingLevel());
        if (record.getMetadata() == null) record.setMetadata(new HashMap<>());
        if (record.getProvenance() == null) {
            record.setProvenance(Provenance.userInput(record.getUserId()));
        }
        if (record.getStrength() == 0.0) record.setStrength(1.0);
        if (record.getUtilityScore() == 0.0) record.setUtilityScore(0.5);
        if (record.getSafetyScore() == 0.0) record.setSafetyScore(0.9);
    }

    private String generateId() {
        return "sem_" + UUID.randomUUID().toString().replace("-", "");
    }

    // ---------- 内部类 ----------

    private static class SemanticRowMapper implements RowMapper<MemoryRecord> {
        private final ObjectMapper objectMapper;
        SemanticRowMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

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
                throw new SQLException("Failed to map semantic memory row", e);
            }
        }
    }

    // ---------- 结果包装 ----------
    public record PageResult<T>(List<T> data, long total) {}
}