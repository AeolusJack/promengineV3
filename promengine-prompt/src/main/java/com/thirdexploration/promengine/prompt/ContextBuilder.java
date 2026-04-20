package com.thirdexploration.promengine.prompt;

import com.thirdexploration.promengine.core.CognitivePhysiology;
import com.thirdexploration.promengine.core.MemoryService;
import com.thirdexploration.promengine.core.ToolInfoProvider;
import com.thirdexploration.promengine.core.domain.*;
import com.thirdexploration.promengine.temporal.SubjectiveTimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private final MemoryService memoryService;
    private final CognitivePhysiology physiology;
    private final SubjectiveTimeService timeService;
    private final ToolInfoProvider toolInfoProvider;

    public Map<String, Object> build(TaskContext ctx) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("user_input", ctx.getUserInput().getText());
        vars.put("cognitive_state", Map.of(
                "focus_mode", physiology.isInFocusMode(),
                "fuel", physiology.getCurrentFuel()
        ));
        vars.put("subjective_time", Map.of(
                "factor", timeService.getDilationFactor()
        ));

        // 检索相关记忆
        Query query = Query.builder()
                .text(ctx.getUserInput().getText())
                .userId(ctx.getUserId())
                .maxResults(5)
                .build();
        RetrievalStrategy strategy = RetrievalStrategy.builder()
                .timeWindow(java.time.Duration.ofDays(30))
                .topK(5)
                .build();
        SearchResult memories = memoryService.retrieve(query, strategy);
        vars.put("long_term_memories", memories.getHits().stream()
                .map(h -> Map.of("summary", h.getContent(), "subjective_age", "recently"))
                .toList());

        vars.put("tools", toolInfoProvider.getAvailableTools().stream().map(t -> t.name()).toList());

        return vars;
    }
}