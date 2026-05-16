package com.thirdexploration.promengine.memory.storage;

import com.thirdexploration.promengine.memory.model.CausalLink;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class Neo4jGraphService {

    @Autowired(required = false)
    private Driver neo4jDriver;

    @Value("${neo4j.max.retry:3}")
    private int maxRetry;

    @Value("${neo4j.retry.delay.ms:100}")
    private long retryDelayMs;

    private boolean isAvailable() {
        if (neo4jDriver == null) {
            log.debug("Neo4j driver not available");
            return false;
        }
        try {
            try (Session session = neo4jDriver.session()) {
                session.run("RETURN 1").consume();
            }
            return true;
        } catch (Exception e) {
            log.debug("Neo4j connection test failed: {}", e.getMessage());
            return false;
        }
    }

    private <T> T executeWithRetry(ThrowingFunction<Session, T> action, String operationName) {
        if (!isAvailable()) {
            log.warn("Neo4j unavailable, skip {}", operationName);
            return null;
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try (Session session = neo4jDriver.session()) {
                return action.apply(session);
            } catch (Exception e) {
                lastException = e;
                log.warn("Neo4j {} attempt {}/{} failed: {}", operationName, attempt, maxRetry, e.getMessage());
                if (attempt < maxRetry) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(retryDelayMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("Neo4j {} failed after {} attempts", operationName, maxRetry, lastException);
        return null;
    }

    public void upsertMemoryNode(MemoryRecord record) {
        if (record == null || record.getId() == null) return;
        executeWithRetry(session -> {
            session.executeWrite(tx -> {
                String query = "MERGE (m:Memory {id: $id}) " +
                        "SET m.content = $content, m.summary = $summary, m.domain = $domain, " +
                        "m.layer = $layer, m.strength = $strength, m.utilityScore = $utilityScore, " +
                        "m.lastUpdated = $lastUpdated";
                tx.run(query, Values.parameters(
                        "id", record.getId(),
                        "content", record.getContent() == null ? "" : record.getContent(),
                        "summary", record.getSummary() == null ? "" : record.getSummary(),
                        "domain", record.getDomain() == null ? "general" : record.getDomain(),
                        "layer", record.getLayer() == null ? "episodic" : record.getLayer(),
                        "strength", record.getStrength(),
                        "utilityScore", record.getUtilityScore(),
                        "lastUpdated", System.currentTimeMillis()
                ));
                return null;
            });
            return null;
        }, "upsertMemoryNode");
    }

    public void batchUpsertMemoryNodes(List<MemoryRecord> records) {
        if (CollectionUtils.isEmpty(records)) return;
        executeWithRetry(session -> {
            session.executeWrite(tx -> {
                String query = "UNWIND $batch AS row " +
                        "MERGE (m:Memory {id: row.id}) " +
                        "SET m.content = row.content, m.summary = row.summary, m.domain = row.domain, " +
                        "m.layer = row.layer, m.strength = row.strength, m.utilityScore = row.utilityScore, " +
                        "m.lastUpdated = row.lastUpdated";
                List<Map<String, Object>> batch = records.stream().map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", r.getId());
                    map.put("content", r.getContent() == null ? "" : r.getContent());
                    map.put("summary", r.getSummary() == null ? "" : r.getSummary());
                    map.put("domain", r.getDomain() == null ? "general" : r.getDomain());
                    map.put("layer", r.getLayer() == null ? "episodic" : r.getLayer());
                    map.put("strength", r.getStrength());
                    map.put("utilityScore", r.getUtilityScore());
                    map.put("lastUpdated", System.currentTimeMillis());
                    return map;
                }).collect(Collectors.toList());
                tx.run(query, Values.parameters("batch", batch));
                return null;
            });
            return null;
        }, "batchUpsertMemoryNodes");
    }

    public void createCausalLink(CausalLink link) {
        if (link == null || link.getSourceMemoryId() == null || link.getTargetMemoryId() == null) return;
        executeWithRetry(session -> {
            session.executeWrite(tx -> {
                String query = "MERGE (source:Memory {id: $sourceId}) " +
                        "ON CREATE SET source.createdAt = timestamp() " +
                        "MERGE (target:Memory {id: $targetId}) " +
                        "ON CREATE SET target.createdAt = timestamp() " +
                        "MERGE (source)-[r:CAUSAL_LINK]->(target) " +
                        "SET r.weight = $weight, r.timestamp = $timestamp";
                tx.run(query, Values.parameters(
                        "sourceId", link.getSourceMemoryId(),
                        "targetId", link.getTargetMemoryId(),
                        "weight", link.getWeight(),
                        "timestamp", System.currentTimeMillis()
                ));
                return null;
            });
            return null;
        }, "createCausalLink");
    }

    public void batchCreateCausalLinks(List<CausalLink> links) {
        if (CollectionUtils.isEmpty(links)) return;
        executeWithRetry(session -> {
            session.executeWrite(tx -> {
                String query = "UNWIND $batch AS row " +
                        "MERGE (source:Memory {id: row.sourceId}) " +
                        "ON CREATE SET source.createdAt = timestamp() " +
                        "MERGE (target:Memory {id: row.targetId}) " +
                        "ON CREATE SET target.createdAt = timestamp() " +
                        "MERGE (source)-[r:CAUSAL_LINK]->(target) " +
                        "SET r.weight = row.weight, r.timestamp = row.timestamp";
                List<Map<String, Object>> batch = links.stream().map(l -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("sourceId", l.getSourceMemoryId());
                    map.put("targetId", l.getTargetMemoryId());
                    map.put("weight", l.getWeight());
                    map.put("timestamp", System.currentTimeMillis());
                    return map;
                }).collect(Collectors.toList());
                tx.run(query, Values.parameters("batch", batch));
                return null;
            });
            return null;
        }, "batchCreateCausalLinks");
    }

    public List<String> expandByRelations(List<String> seedIds, int maxDepth, int maxResults) {
        if (CollectionUtils.isEmpty(seedIds) || maxDepth <= 0 || maxResults <= 0) {
            return Collections.emptyList();
        }
        int effectiveDepth = Math.min(maxDepth, 5);
        int effectiveLimit = Math.min(maxResults, 500);

        List<String> result = executeWithRetry(session -> {
            String query = "MATCH (start:Memory)-[:CAUSAL_LINK*1.." + effectiveDepth + "]-(neighbor:Memory) " +
                    "WHERE start.id IN $seedIds AND NOT neighbor.id IN $seedIds " +
                    "RETURN DISTINCT neighbor.id AS id LIMIT $limit";
            Result resultSet = session.run(query,
                    Values.parameters("seedIds", seedIds, "limit", effectiveLimit));
            List<String> ids = new ArrayList<>();
            while (resultSet.hasNext()) {
                ids.add(resultSet.next().get("id").asString());
            }
            return ids;
        }, "expandByRelations");

        return result != null ? result : Collections.emptyList();
    }

    public List<String> expandByRelations(List<String> seedIds) {
        return expandByRelations(seedIds, 2, 20);
    }

    public List<String> getDirectNeighbors(String nodeId, int maxResults) {
        if (nodeId == null || maxResults <= 0) return Collections.emptyList();
        List<String> result = executeWithRetry(session -> {
            String query = "MATCH (m:Memory {id: $id})-[:CAUSAL_LINK]-(neighbor) " +
                    "RETURN DISTINCT neighbor.id AS id LIMIT $limit";
            Result resultSet = session.run(query,
                    Values.parameters("id", nodeId, "limit", maxResults));
            List<String> ids = new ArrayList<>();
            while (resultSet.hasNext()) {
                ids.add(resultSet.next().get("id").asString());
            }
            return ids;
        }, "getDirectNeighbors");
        return result != null ? result : Collections.emptyList();
    }

    public void deleteMemoryNode(String id) {
        if (id == null) return;
        executeWithRetry(session -> {
            session.executeWrite(tx -> {
                tx.run("MATCH (m:Memory {id: $id}) DETACH DELETE m",
                        Values.parameters("id", id));
                return null;
            });
            return null;
        }, "deleteMemoryNode");
    }

    public void batchDeleteMemoryNodes(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) return;
        executeWithRetry(session -> {
            session.executeWrite(tx -> {
                String query = "MATCH (m:Memory) WHERE m.id IN $ids DETACH DELETE m";
                tx.run(query, Values.parameters("ids", ids));
                return null;
            });
            return null;
        }, "batchDeleteMemoryNodes");
    }

    public Map<String, Object> getStats() {
        if (!isAvailable()) return Collections.singletonMap("available", false);
        return executeWithRetry(session -> {
            Result nodeResult = session.run("MATCH (n:Memory) RETURN count(n) AS nodeCount");
            long nodeCount = nodeResult.single().get("nodeCount").asLong();
            Result relResult = session.run("MATCH ()-[r:CAUSAL_LINK]->() RETURN count(r) AS relCount");
            long relCount = relResult.single().get("relCount").asLong();
            Map<String, Object> stats = new HashMap<>();
            stats.put("available", true);
            stats.put("nodeCount", nodeCount);
            stats.put("relationshipCount", relCount);
            return stats;
        }, "getStats");
    }

    @FunctionalInterface
    private interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }
}