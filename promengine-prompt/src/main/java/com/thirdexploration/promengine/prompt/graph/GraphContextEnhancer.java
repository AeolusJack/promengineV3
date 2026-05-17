package com.thirdexploration.promengine.prompt.graph;

import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.storage.Neo4jGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class GraphContextEnhancer {

    @Autowired(required = false)
    private Neo4jGraphService graphService;


    public String buildGraphSection(List<MemoryEntry> memories, String queryText) {
        if (graphService == null || memories.isEmpty()) return "";

        List<String> seedIds = memories.stream().map(MemoryEntry::getId).collect(Collectors.toList());
        // 获取邻居节点 ID（只获取 ID，不获取全量记录）
        List<String> neighborIds = graphService.expandByRelations(seedIds, 2, 30);
        if (neighborIds.isEmpty()) return "";

        // 构建节点摘要映射（从传入的 memories 中提取，邻居节点可能缺失）
        Map<String, String> nodeSummaries = new HashMap<>();
        for (MemoryEntry m : memories) {
            nodeSummaries.put(m.getId(), m.getSummary() != null ? m.getSummary() : truncate(m.getContent(), 80));
        }

        // 获取真实边
        List<Map<String, String>> edges = graphService.getCausalEdges(seedIds, neighborIds, 20);
        if (edges.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\n【知识图谱关联】\n");
        for (Map<String, String> edge : edges) {
            String sourceId = edge.get("sourceId");
            String targetId = edge.get("targetId");
            String type = edge.get("relationType");
            String sourceSummary = nodeSummaries.getOrDefault(sourceId, sourceId);
            String targetSummary = nodeSummaries.getOrDefault(targetId, targetId);
            sb.append("- ").append(formatRelation(type, sourceSummary, targetSummary)).append("\n");
        }
        return sb.toString();
    }

    private String formatRelation(String type, String source, String target) {
        return switch (type) {
            case "CAUSED_BY" -> String.format("\"%s\" 是由 \"%s\" 导致的", source, target);
            case "SUPPORTS" -> String.format("\"%s\" 支持了 \"%s\" 的结论", source, target);
            case "CONTRADICTS" -> String.format("\"%s\" 与 \"%s\" 存在矛盾", source, target);
            case "IMPLEMENTS" -> String.format("\"%s\" 实现了 \"%s\"", source, target);
            case "DERIVED_FROM" -> String.format("\"%s\" 源自 \"%s\"", source, target);
            default -> String.format("\"%s\" 关联 \"%s\"", source, target);
        };
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    record Edge(String sourceId, String targetId, String type) {}
}