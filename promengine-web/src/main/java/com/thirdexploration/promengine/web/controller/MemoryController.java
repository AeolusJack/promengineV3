package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.core.context.VisibilityContext;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import com.thirdexploration.promengine.memory.governance.ProceduralMemoryGate;
import com.thirdexploration.promengine.memory.model.*;
import com.thirdexploration.promengine.memory.retrieval.EnhancedRetrievalOrchestrator;
import com.thirdexploration.promengine.memory.storage.EpisodicMemoryService;
import com.thirdexploration.promengine.memory.storage.ProceduralMemoryService;
import com.thirdexploration.promengine.memory.storage.SemanticMemoryService;
import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final UnifiedMemoryAPI unifiedMemoryAPI;
    private final EpisodicMemoryService episodicMemoryService;
    private final SemanticMemoryService semanticMemoryService;
    private final ProceduralMemoryService proceduralMemoryService;
    private final ProceduralMemoryGate proceduralMemoryGate;
    private final EnhancedRetrievalOrchestrator retrievalOrchestrator;

    @PostMapping("/remember")
    public ApiResponse<Void> remember(@RequestBody Map<String, Object> body,
                                      @RequestHeader("X-User-Id") String userId) {
        String content = (String) body.get("content");
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) body.getOrDefault("metadata", new HashMap<>());
        MemoryMetadata metadata = MemoryMetadata.builder()
                .userId(userId)
                .domain((String) meta.getOrDefault("domain", "general"))
                .importance(((Number) meta.getOrDefault("importance", 0.5f)).floatValue())
                .sessionId((String) meta.get("sessionId"))
                .sharingLevel((String) meta.getOrDefault("sharingLevel", "private"))
                .source((String) meta.getOrDefault("source", "api"))
                .build();
        unifiedMemoryAPI.remember(content, metadata);
        return ApiResponse.ok(null);
    }

    @PostMapping("/recall")
    public ApiResponse<List<MemoryEntry>> recall(@RequestBody Map<String, Object> body,
                                                 @RequestHeader("X-User-Id") String userId) {
        String text = (String) body.get("text");
        String domain = (String) body.getOrDefault("domain", "general");
        int maxResults = body.containsKey("maxResults") ? ((Number) body.get("maxResults")).intValue() : 10;
        MemoryQuery query = MemoryQuery.builder()
                .text(text)
                .userId(userId)
                .domain(domain)
                .maxResults(maxResults)
                .currentUserId(userId)
                .currentTeamIds(VisibilityContext.get().getTeamIds())
                .currentTenantId(VisibilityContext.get().getTenantId())
                // 用户可通过参数指定最小共享级别，默认为 private
                .minSharingLevel((String) body.getOrDefault("minSharingLevel", "private"))
                .build();
        List<MemoryEntry> entries = unifiedMemoryAPI.recall(query);
        return ApiResponse.ok(entries);
    }

    @PostMapping("/{id}/quality")
    public ApiResponse<Void> markQuality(@PathVariable String id,
                                         @RequestBody Map<String, String> body) {
        String action = body.get("action");
        if (!"good".equals(action) && !"bad".equals(action)) {
            return ApiResponse.error("Invalid action, must be 'good' or 'bad'");
        }
        double utilityDelta = "good".equals(action) ? 0.2 : -0.3;
        double safetyDelta = "good".equals(action) ? 0.1 : -0.2;
        updateMemoryScore(id, utilityDelta, safetyDelta);
        return ApiResponse.ok(null);
    }

    @PostMapping("/quality/batch")
    public ApiResponse<Void> batchMarkQuality(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        String action = (String) body.get("action");
        if (!"good".equals(action) && !"bad".equals(action)) {
            return ApiResponse.error("Invalid action, must be 'good' or 'bad'");
        }
        double utilityDelta = "good".equals(action) ? 0.2 : -0.3;
        double safetyDelta = "good".equals(action) ? 0.1 : -0.2;
        for (String id : ids) {
            updateMemoryScore(id, utilityDelta, safetyDelta);
        }
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/convert-to-procedural")
    public ApiResponse<Void> convertToProcedural(@PathVariable String id) {
        MemoryRecord record = findRecordById(id);
        if (record == null) return ApiResponse.error("Memory not found");

        if (!proceduralMemoryGate.evaluate(record)) {
            return ApiResponse.error("该记忆不符合过程记忆的准入标准。"
                    + "过程记忆需要：来源为工具输出或Agent生成、包含清晰的操作步骤、"
                    + "效用评分 ≥ 0.7、安全评分 ≥ 0.5。");
        }
        String originalLayer = record.getLayer();
        String newId = "proc_" + UUID.randomUUID().toString().replace("-", "");
        record.setId(newId);
        record.setLayer("procedural");
        proceduralMemoryService.store(record);

        if ("episodic".equals(originalLayer)) {
            episodicMemoryService.softDelete(id);
        } else if ("semantic".equals(originalLayer)) {
            semanticMemoryService.softDelete(id);
        } else {
            log.warn("Unsupported original layer: {}", originalLayer);
        }
        return ApiResponse.ok(null);
    }


    @PostMapping("/retrieval/debug")
    public ApiResponse<Map<String, Object>> debugRetrieval(@RequestBody Map<String, Object> body,
                                                           @RequestHeader("X-User-Id") String userId) {
        String query = (String) body.get("query");
        int topK = body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 10;

        MemoryQuery memoryQuery = MemoryQuery.builder()
                .text(query)
                .userId(userId)
                .maxResults(topK)
                .includeWorking(false)      // 可保留，但当前前端不传 sessionId
                .includeEpisodic(true)
                .includeSemantic(true)
                .build();

        Map<String, Object> result = retrievalOrchestrator.debugRetrieve(memoryQuery);
        return ApiResponse.ok(result);
    }

    @GetMapping("/{id}/decay-curve")
    public ApiResponse<Map<String, Object>> getDecayCurve(@PathVariable String id) {
        MemoryRecord record = findRecordById(id);
        if (record == null) return ApiResponse.error("Not found");

        List<Map<String, Double>> points = new ArrayList<>();
        double initialStrength = record.getStrength();
        for (int d = 0; d <= 30; d++) {
            double strength = initialStrength * Math.exp(-0.1 * d);
            points.add(Map.of("days", (double) d, "strength", Math.max(0.0, strength)));
        }
        return ApiResponse.ok(Map.of("points", points));
    }

    // ---------- 辅助方法 ----------
    private MemoryRecord findRecordById(String id) {
        MemoryRecord record = episodicMemoryService.findById(id);
        if (record == null) {
            record = semanticMemoryService.findById(id);
        }
        if (record == null) {
            record = proceduralMemoryService.findById(id);
        }
        return record;
    }

    private void updateMemoryScore(String id, double utilityDelta, double safetyDelta) {
        MemoryRecord record = findRecordById(id);
        if (record == null) {
            return;
        }
        double newUtility = Math.min(1.0, Math.max(0.0, record.getUtilityScore() + utilityDelta));
        double newSafety = Math.min(1.0, Math.max(0.0, record.getSafetyScore() + safetyDelta));

        switch (record.getLayer()) {
            case "episodic" -> episodicMemoryService.updateScores(id, newUtility, newSafety);
            case "semantic" -> semanticMemoryService.updateScores(id, newUtility, newSafety);
            case "procedural" -> { /* 过程记忆暂不实现，可扩展 */ }
        }
    }
}