package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.storage.EpisodicMemoryService;
import com.thirdexploration.promengine.memory.storage.ProceduralMemoryService;
import com.thirdexploration.promengine.memory.storage.SemanticMemoryService;
import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/memory")
@RequiredArgsConstructor
public class MemoryLayerController {

    private final EpisodicMemoryService episodicMemoryService;
    private final SemanticMemoryService semanticMemoryService;
    private final ProceduralMemoryService proceduralMemoryService;

    @GetMapping("/layers")
    public ApiResponse<Map<String, Object>> getLayers() {
        Map<String, Long> layerCounts = new LinkedHashMap<>();
        layerCounts.put("working", 0L);
        layerCounts.put("episodic", episodicMemoryService.countAll());
        layerCounts.put("semantic", semanticMemoryService.countAll());
        layerCounts.put("procedural", proceduralMemoryService.countAll());
        layerCounts.put("collective", 0L);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("layers", layerCounts.keySet().toArray(String[]::new));
        result.put("counts", layerCounts);
        return ApiResponse.ok(result);
    }

    @GetMapping("/layer/{layerName}")
    public ApiResponse<Map<String, Object>> getMemoriesByLayer(@PathVariable String layerName,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int size,
                                                               @RequestParam(required = false) String keyword) {
        List<MemoryEntry> memories;
        long total;

        if ("episodic".equalsIgnoreCase(layerName)) {
            var result = episodicMemoryService.findByKeywordAndPage(keyword, page, size);
            memories = result.data();
            total = result.total();
        } else if ("semantic".equalsIgnoreCase(layerName)) {
            var result = semanticMemoryService.findByKeywordAndPage(keyword, page, size);
            memories = result.data();
            total = result.total();
        } else if ("procedural".equalsIgnoreCase(layerName)) {
            var result = proceduralMemoryService.findByKeywordAndPage(keyword, page, size);
            memories = result.data();
            total = result.total();
        } else {
            return ApiResponse.ok(Map.of("data", List.of(), "total", 0));
        }

        return ApiResponse.ok(Map.of("data", memories, "total", total));
    }
}