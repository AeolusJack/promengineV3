package com.thirdexploration.promengine.prompt.pipeline;

import com.thirdexploration.promengine.core.CognitivePhysiology;
import com.thirdexploration.promengine.core.ToolInfoProvider;
import com.thirdexploration.promengine.core.domain.TaskContext;
import com.thirdexploration.promengine.memory.api.UnifiedMemoryAPI;
import com.thirdexploration.promengine.memory.injection.MemoryInjectionPipeline;
import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import com.thirdexploration.promengine.prompt.RenderEngine;
import com.thirdexploration.promengine.prompt.TemplateRegistry;
import com.thirdexploration.promengine.prompt.compression.PromptCompressor;
import com.thirdexploration.promengine.prompt.config.PromptProperties;
import com.thirdexploration.promengine.prompt.core.PromptContext;
import com.thirdexploration.promengine.prompt.core.PromptContextBuilder;
import com.thirdexploration.promengine.prompt.core.PromptPipeline;
import com.thirdexploration.promengine.prompt.graph.GraphContextEnhancer;
import com.thirdexploration.promengine.prompt.model.PromptTemplate;

import com.thirdexploration.promengine.prompt.window.ContextWindowManager;
import com.thirdexploration.promengine.temporal.SubjectiveTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultPromptPipeline implements PromptPipeline, PromptContextBuilder {

    private final UnifiedMemoryAPI memoryAPI;
    private final CognitivePhysiology physiology;
    private final SubjectiveTimeService timeService;
    private final ToolInfoProvider toolInfoProvider;
    private final PromptProperties properties;

    // 直接注入渲染所需组件，不再依赖 PromptManager
    private final TemplateRegistry templateRegistry;
    private final RenderEngine renderEngine;
    private final PromptCompressor compressor;


    private final ContextWindowManager windowManager; // 新增


    @Override
    public PromptContext collect(TaskContext ctx) {
        return build(ctx);
    }

    @Override
    public PromptContext build(TaskContext ctx) {
        // 1. 检索记忆
        MemoryQuery memoryQuery = MemoryQuery.builder()
                .text(ctx.getUserInput().getText())
                .userId(ctx.getUserId())
                .sessionId(ctx.getUserInput().getSessionId())
                .includeWorking(true)
                .includeEpisodic(true)
                .includeSemantic(true)
                .maxResults(properties.getDefaultTopK())
                .build();

        List<MemoryEntry> memories = memoryAPI.recall(memoryQuery);
        log.debug("Retrieved {} memories for prompt building", memories.size());

        // 2. 收集认知状态
        Map<String, Object> cognitiveState = new HashMap<>();
        cognitiveState.put("focus_mode", physiology.isInFocusMode());
        cognitiveState.put("fuel", physiology.getCurrentFuel());
        cognitiveState.put("subjective_time", Map.of("factor", timeService.getDilationFactor()));

        // 3. 工具描述
        String toolDescriptions = toolInfoProvider.getToolDescriptions();

        // 裁剪记忆
        List<MemoryEntry> trimmedMemories = windowManager.trimMemories(memories, 4000); // 可配置
        // 工具描述裁剪
        String toolsDesc = toolInfoProvider.getToolDescriptions();
        toolsDesc = windowManager.trimToolsDescription(toolsDesc, 1000);

        // 4. 构建 PromptContext
        return PromptContext.builder()
                .userId(ctx.getUserId())
                .sessionId(ctx.getUserInput().getSessionId())
                .userInput(ctx.getUserInput().getText())
                .taskType(ctx.getTaskType())
                .cognitiveState(cognitiveState)
//                .memories(memories)
                .availableTools(toolInfoProvider.getAvailableToolNames())
//                .toolDescriptions(toolDescriptions)
                .extraVariables(ctx.getVariables())
                .memories(trimmedMemories)
                .toolDescriptions(toolsDesc)
                .build();
    }

    private  final  GraphContextEnhancer graphEnhancer;
    private  final  MemoryInjectionPipeline memoryInjectionPipeline;
    @Override
    public String render(PromptContext context) {
        String templateId = context.getTaskType() != null ? context.getTaskType() : properties.getDefaultTemplate();
        PromptTemplate template = templateRegistry.get(templateId);
        if (template == null) {
            template = templateRegistry.get(properties.getDefaultTemplate());
        }

        // 1. 处理记忆注入（生成单独的记忆上下文）
        String memorySection = buildMemorySection(context.getMemories(), context.getUserInput());

        // 2. 图上下文
        String graphSection = (graphEnhancer != null)
                ? graphEnhancer.buildGraphSection(context.getMemories(), context.getUserInput())
                : "";
        // 3. 构建变量，将记忆段和图段作为单独变量
        Map<String, Object> variables = buildVariables(context);
        variables.put("memory_section", memorySection);
        variables.put("graph_section", graphSection);

        // 4. 渲染模板（模板中可使用 {{ memory_section }} 和 {{ graph_section }}）
        return renderEngine.render(template, variables);
    }

    private String buildMemorySection(List<MemoryEntry> memories, String query) {
        if (memories.isEmpty()) return "";
        // 使用新的注入管道
        List<MemoryInjectionPipeline.MemoryInjectionItem> items = memoryInjectionPipeline.process(memories, query, 2000);
        if (items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n<background_knowledge>\n");
        for (var item : items) {
            sb.append(String.format("- [%s] %s\n", item.layer(), item.summary()));
        }
        sb.append("</background_knowledge>\n");
        return sb.toString();
    }

    @Override
    public String compress(String prompt) {
        if (!properties.getCompression().isEnabled()) {
            return prompt;
        }
        return compressor.compress(prompt, properties.getCompression().getTargetMaxTokens());
    }

    private Map<String, Object> buildVariables(PromptContext context) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("user_input", context.getUserInput());
        vars.put("cognitive_state", context.getCognitiveState());
        vars.put("long_term_memories", context.getMemories().stream()
                .map(m -> Map.of(
                        "summary", m.getSummary() != null ? m.getSummary() : truncate(m.getContent(), 100),
                        "content", m.getContent(),
                        "domain", m.getDomain()
                ))
                .toList());
        vars.put("tools", context.getAvailableTools());
        vars.put("tool_descriptions", context.getToolDescriptions());
        if (context.getExtraVariables() != null) {
            vars.putAll(context.getExtraVariables());
        }
        return vars;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}