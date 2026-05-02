package com.thirdexploration.promengine.memory.storage;

import com.thirdexploration.promengine.memory.model.CausalLink;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class Neo4jGraphService {

    @Autowired(required = false)
    private Driver neo4jDriver;

    private boolean isAvailable() {
        return neo4jDriver != null;
    }

    public void upsertMemoryNode(MemoryRecord record) {
        if (!isAvailable()) {
            log.debug("Neo4j is not available, skip upsert");
            return;
        }
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                String query = """
                        MERGE (m:Memory {id: $id})
                        SET m.content = $content,
                            m.summary = $summary,
                            m.domain = $domain,
                            m.layer = $layer,
                            m.strength = $strength,
                            m.utilityScore = $utilityScore
                        """;
                tx.run(query, Values.parameters(
                        "id", record.getId(),
                        "content", record.getContent(),
                        "summary", record.getSummary(),
                        "domain", record.getDomain(),
                        "layer", record.getLayer(),
                        "strength", record.getStrength(),
                        "utilityScore", record.getUtilityScore()
                ));
                return null;
            });
        } catch (Exception e) {
            log.warn("Failed to upsert memory node in Neo4j: {}", e.getMessage());
        }
    }

    public void createCausalLink(CausalLink link) {
        if (!isAvailable()) return;
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (a:Memory {id: $s}) MATCH (b:Memory {id: $t}) MERGE (a)-[:CAUSAL_LINK]->(b)",
                        Values.parameters("s", link.getSourceMemoryId(), "t", link.getTargetMemoryId()));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j causal link creation failed: {}", e.getMessage());
        }
    }

    public List<String> expandByRelations(List<String> seedIds) {
        if (!isAvailable() || seedIds.isEmpty()) return List.of();
        try (Session session = neo4jDriver.session()) {
            return session.readTransaction(tx -> {
                var result = tx.run(
                        "MATCH (m:Memory)-[:CAUSAL_LINK]-(other) WHERE m.id IN $ids RETURN DISTINCT other.id AS id",
                        Values.parameters("ids", seedIds));
                return result.list(record -> record.get("id").asString());
            });
        } catch (Exception e) {
            log.warn("Neo4j expand relations failed: {}", e.getMessage());
            return List.of();
        }
    }

    public void deleteMemoryNode(String id) {
        if (!isAvailable()) return;
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (m:Memory {id: $id}) DETACH DELETE m",
                        Values.parameters("id", id));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j delete node failed: {}", e.getMessage());
        }
    }
}