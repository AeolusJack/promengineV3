package com.thirdexploration.promengine.memory.storage;

import com.thirdexploration.promengine.memory.model.CausalLink;
import com.thirdexploration.promengine.memory.model.MemoryRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * aeon
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aeon.memory.graph.enabled", havingValue = "true")
public class Neo4jGraphService {

    private final Driver neo4jDriver;

    public void upsertMemoryNode(MemoryRecord record) {
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
                tx.run(query, org.neo4j.driver.Values.parameters(
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
        }
    }

    public void createCausalLink(CausalLink link) {
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                String query = """
                        MATCH (a:Memory {id: $sourceId})
                        MATCH (b:Memory {id: $targetId})
                        MERGE (a)-[r:CAUSAL_LINK {type: $type, confidence: $confidence}]->(b)
                        """;
                tx.run(query, org.neo4j.driver.Values.parameters(
                        "sourceId", link.getSourceMemoryId(),
                        "targetId", link.getTargetMemoryId(),
                        "type", link.getRelationType().name(),
                        "confidence", link.getConfidence()
                ));
                return null;
            });
        }
    }

    public List<String> expandByRelations(List<String> seedIds) {
        // 根据种子节点扩展关联记忆 ID
        //todo
        return List.of();
    }

    public void deleteMemoryNode(String id) {
        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (m:Memory {id: $id}) DETACH DELETE m",
                        org.neo4j.driver.Values.parameters("id", id));
                return null;
            });
        }
    }
}