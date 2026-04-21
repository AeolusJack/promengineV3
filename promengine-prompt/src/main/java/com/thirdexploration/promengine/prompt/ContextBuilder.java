package com.thirdexploration.promengine.prompt;

import com.thirdexploration.promengine.core.CognitivePhysiology;
import com.thirdexploration.promengine.core.ToolInfoProvider;
import com.thirdexploration.promengine.core.domain.TaskContext;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.temporal.SubjectiveTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文构建器，用于为提示词模板准备变量。
 * 从各个子系统收集信息，包括记忆、认知状态、可用工具等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private final UnifiedMemoryAPI memoryAPI;
    private final CognitivePhysiology physiology;
    private final SubjectiveTimeService timeService;
    private final ToolInfoProvider toolInfoProvider;

    /**
     * 构建模板变量。
     * @param ctx 任务上下文，包含用户输入、用户ID、会话ID等
     * @return 变量映射
     */
    public Map<String, Object> build(TaskContext ctx) {
        Map<String, Object> vars = new HashMap<>();

        // 用户输入
        vars.put("user_input", ctx.getUserInput().getText());

        // 认知状态
        vars.put("cognitive_state", Map.of(
                "focus_mode", physiology.isInFocusMode(),
                "fuel", physiology.getCurrentFuel()
        ));

        // 主观时间
        vars.put("subjective_time", Map.of(
                "factor", timeService.getDilationFactor()
        ));

        // 检索相关记忆（使用新的 MemoryQuery）
        MemoryQuery memoryQuery = MemoryQuery.builder()
                .text(ctx.getUserInput().getText())
                .userId(ctx.getUserId())
                .sessionId(ctx.getUserInput().getSessionId())   // 传递会话ID，以便检索情景记忆时过滤
                .includeWorking(true)
                .includeEpisodic(true)
                .includeSemantic(true)
                .maxResults(5)
                .build();

        List<MemoryEntry> memories = memoryAPI.recall(memoryQuery);
        log.debug("Retrieved {} memories for prompt building", memories.size());

        vars.put("long_term_memories", memories.stream()
                .map(m -> Map.of(
                        "summary", m.getSummary() != null ? m.getSummary() : truncate(m.getContent(), 100),
                        "content", m.getContent(),
                        "subjective_age", "recently",   // 可进一步集成主观时间服务计算
                        "domain", m.getDomain()
                ))
                .toList());

        // 可用工具列表
        vars.put("tools", toolInfoProvider.getAvailableToolNames());

        return vars;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}