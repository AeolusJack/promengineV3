package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.storage.EpisodicMemoryService;
import com.thirdexploration.promengine.memory.storage.SemanticMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/memory")
@RequiredArgsConstructor
public class MemoryLayerController {

    private final EpisodicMemoryService episodicMemoryService;
    private final SemanticMemoryService semanticMemoryService;


    @GetMapping("/layers")
    public Map<String, Object> getLayers() {
        Map<String, Long> layerCounts = new LinkedHashMap<>();
        layerCounts.put("working", 0L);       // 工作记忆暂不统计
        layerCounts.put("episodic", episodicMemoryService.countAll());
        layerCounts.put("semantic", semanticMemoryService.countAll());
        layerCounts.put("procedural", 0L);    // 其他层级暂不统计，后续可扩展
        layerCounts.put("collective", 0L);

        return Map.of(
                "layers", layerCounts.keySet().toArray(String[]::new),
                "counts", layerCounts
        );
    }
    @GetMapping("/layer/{layerName}")
    public Map<String, Object> getMemoriesByLayer(@PathVariable String layerName,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String keyword) {
        List<MemoryEntry> memories;
        long total;

        if ("episodic".equalsIgnoreCase(layerName)) {
            // 使用 EpisodicMemoryService 分页查询
            var result = episodicMemoryService.findByKeywordAndPage(keyword, page, size);
            memories = result.data();
            total = result.total();
        } else if ("semantic".equalsIgnoreCase(layerName)) {
            // 使用 SemanticMemoryService 分页查询
            var result = semanticMemoryService.findByKeywordAndPage(keyword, page, size);
            memories = result.data();
            total = result.total();
        } else {
            // 其他层级暂返回空
            return Map.of("data", List.of(), "total", 0);
        }

        return Map.of(
                "data", memories,
                "total", total
        );
    }
}