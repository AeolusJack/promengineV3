package com.thirdexploration.promengine.memory.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.memory.config.MemoryMetadataRegistry;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.model.Provenance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
//@RequiredArgsConstructor
public class SemanticMemoryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryMetadataRegistry registry;
    private final VectorStorage vectorStorage;
    // 非必需依赖，当图谱功能未启用时可以为 null
    @Autowired(required = false)
    private  Neo4jGraphService graphService; // 可能为 null
    // 显式构造器，只注入必需依赖
    public SemanticMemoryService(JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper,
                                 MemoryMetadataRegistry registry,
                                 VectorStorage vectorStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.vectorStorage = vectorStorage;
    }

    public List<MemoryRecord> semanticSearch(String queryText, int topK) {
        if (vectorStorage == null) return List.of();
        List<VectorStorage.SearchHit> hits = vectorStorage.searchByText(queryText, topK);
        return hits.stream()
                .map(hit -> findById(hit.id()))
                .filter(Objects::nonNull)
                .toList();
    }
    public void updateScores(String id, double utilityScore, double safetyScore) {
        String sql = "UPDATE semantic_memory SET utility_score = ?, safety_score = ? WHERE id = ?";
        jdbcTemplate.update(sql, utilityScore, safetyScore, id);
        log.debug("Updated scores for semantic memory: id={}", id);
    }

    private static final String INSERT_SQL = """
            INSERT INTO semantic_memory
            (id, user_id, content, summary, timestamp, memory_type, importance,
             metadata, domain, project_id, strength, layer, utility_score,
             safety_score, sharing_level, provenance, retrieval_count, vector_id, session_id,created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)
            """;

    private static final String SELECT_BY_ID = """
            SELECT * FROM semantic_memory WHERE id = ? AND deleted = 0
            """;

    private static final String SELECT_BY_IDS = """
            SELECT * FROM semantic_memory WHERE id IN (%s) AND deleted = 0
            """;

    private static final String UPDATE_STRENGTH = """
            UPDATE semantic_memory SET strength = ? WHERE id = ?
            """;

    private static final String SOFT_DELETE = """
            UPDATE semantic_memory SET deleted = 1, deleted_at = ? WHERE id = ?
            """;

    private static final String HARD_DELETE = "DELETE FROM semantic_memory WHERE id = ?";

    // 统计 SQL
    private static final String COUNT_ALL = "SELECT COUNT(*) FROM semantic_memory WHERE deleted = 0";
    private static final String COUNT_BY_USER = "SELECT COUNT(*) FROM semantic_memory WHERE user_id = ? AND deleted = 0";



    /**
     * 分页关键词查询（用于前端记忆列表展示）
     */
    public record PageResult<T>(List<T> data, long total) {}

    public PageResult<MemoryEntry> findByKeywordAndPage(String keyword, int page, int size) {
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM semantic_memory WHERE deleted = 0 ");
        StringBuilder dataSql = new StringBuilder("SELECT * FROM semantic_memory WHERE deleted = 0 ");

        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword + "%";
            String where = " AND (content LIKE ? OR summary LIKE ?) ";
            countSql.append(where);
            dataSql.append(where);
            params.add(like);
            params.add(like);
        }

        long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, params.toArray());

        dataSql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        int offset = (page - 1) * size;
        params.add(size);
        params.add(offset);

        List<MemoryRecord> data = jdbcTemplate.query(dataSql.toString(), new SemanticRowMapper(objectMapper), params.toArray());
        List<MemoryEntry> list = data.stream().map(r -> MemoryEntry.builder()
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
                .build()
        ).toList();

        return new PageResult<>(list, total);
    }

    @Transactional
    public void store(MemoryRecord record) {
        if (record.getId() == null) record.setId(generateId());
        record.setLayer("semantic");
        if (record.getDomain() == null) record.setDomain(registry.getDefaultDomain());
        if (record.getSharingLevel() == null) record.setSharingLevel(registry.getDefaultSharingLevel());

        try {
            String vectorId = null;
            if (vectorStorage != null && record.getVector() != null) {
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
                    record.getSessionId()   // 仅存储，不用于检索过滤
                    ,
                    System.currentTimeMillis()   // created_at
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
        List<Object[]> batchArgs = new ArrayList<>();
        for (MemoryRecord rec : records) {
            if (rec.getId() == null) rec.setId(generateId());
            rec.setLayer("semantic");
            if (rec.getDomain() == null) rec.setDomain(registry.getDefaultDomain());
            if (rec.getSharingLevel() == null) rec.setSharingLevel(registry.getDefaultSharingLevel());

            try {
                String vectorId = null;
                if (vectorStorage != null && rec.getVector() != null) {
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
                        vectorId, rec.getSessionId()
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

    public List<MemoryRecord> semanticSearch(float[] queryVector, int topK) {
        if (vectorStorage == null || queryVector == null) return List.of();
        List<VectorStorage.SearchHit> hits = vectorStorage.search(queryVector, topK);
        if (hits.isEmpty()) return List.of();
        List<String> ids = hits.stream().map(VectorStorage.SearchHit::id).toList();
        return findByIds(ids);
    }

    public MemoryRecord findById(String id) {
        try {
            return jdbcTemplate.queryForObject(SELECT_BY_ID, new SemanticRowMapper(objectMapper), id);
        } catch (Exception e) {
            return null;
        }
    }

    public List<MemoryRecord> findByIds(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        String inClause = String.join(",", ids.stream().map(id -> "'" + id + "'").toList());
        String sql = String.format(SELECT_BY_IDS, inClause);
        return jdbcTemplate.query(sql, new SemanticRowMapper(objectMapper));
    }

    public List<String> getAllIds() {
        String sql = "SELECT id FROM semantic_memory WHERE deleted = 0";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    public void updateStrength(String id, float newStrength) {
        jdbcTemplate.update(UPDATE_STRENGTH, newStrength, id);
    }

    public void softDelete(String id) {
        jdbcTemplate.update(SOFT_DELETE, System.currentTimeMillis(), id);
        if (vectorStorage != null) vectorStorage.delete(id);
        if (graphService != null) graphService.deleteMemoryNode(id);
    }

    public void hardDelete(String id) {
        jdbcTemplate.update(HARD_DELETE, id);
        if (vectorStorage != null) vectorStorage.delete(id);
        if (graphService != null) graphService.deleteMemoryNode(id);
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
        return "sem_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static class SemanticRowMapper implements RowMapper<MemoryRecord> {
        private final ObjectMapper objectMapper;
        SemanticRowMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

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
                        .utilityScore(rs.getFloat("utility_score"))
                        .safetyScore(rs.getFloat("safety_score"))
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
}