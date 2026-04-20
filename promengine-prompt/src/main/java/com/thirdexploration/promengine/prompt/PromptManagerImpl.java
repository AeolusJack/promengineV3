package com.thirdexploration.promengine.prompt;

import com.thirdexploration.promengine.core.PromptManager;
import com.thirdexploration.promengine.core.domain.RenderedPrompt;
import com.thirdexploration.promengine.core.domain.TaskContext;
import com.thirdexploration.promengine.prompt.model.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptManagerImpl implements PromptManager {

    private final TemplateRegistry templateRegistry;
    private final ContextBuilder contextBuilder;
    private final RenderEngine renderEngine;
    private final PromptCompressor compressor;
    private final PromptProperties properties;

    private final Map<String, Map<String, Feedback>> feedbackStore = new ConcurrentHashMap<>();

    @Override
    public RenderedPrompt render(TaskContext ctx) {

        String templateId = ctx.getTaskType() != null ? ctx.getTaskType() : properties.getDefaultTemplate();
        PromptTemplate template = templateRegistry.get(templateId);
        if (template == null) {
            template = templateRegistry.get(properties.getDefaultTemplate());
        }

        Map<String, Object> variables = contextBuilder.build(ctx);
        String rendered = renderEngine.render(template, variables);

        if (properties.getCompression().isEnabled()) {
            rendered = compressor.compress(rendered, properties.getCompression().getTargetMaxTokens());
        }

        //观测日志开始
        String finalPrompt = rendered;
        // 日志输出（截断处理，避免过长）
        String preview = finalPrompt.length() > 500
                ? finalPrompt.substring(0, 500) + "... [总长度: " + finalPrompt.length() + "]"
                : finalPrompt;
        log.info("=== 系统提示词 ===\n{}", preview);
        //观测日志结束

        return RenderedPrompt.builder()
                .templateId(templateId)
                .version(template.getVersion())
                .finalPrompt(rendered)
                .tokenCount(estimateTokens(rendered))
                .build();
    }

    @Override
    public void registerTemplate(com.thirdexploration.promengine.core.domain.Template coreTemplate) {
        PromptTemplate template = PromptTemplate.builder()
                .id(coreTemplate.getId())
                .name(coreTemplate.getName())
                .version(coreTemplate.getVersion())
                .content(coreTemplate.getContent())
                .mode(coreTemplate.getMode())
                .build();
        templateRegistry.register(template);
    }

    @Override
    public void recordFeedback(String templateId, String version, Feedback feedback) {
        feedbackStore.computeIfAbsent(templateId, k -> new ConcurrentHashMap<>())
                .put(version, feedback);
        log.debug("Recorded feedback for template {} v{}: rating={}", templateId, version, feedback.getRating());
    }

    private int estimateTokens(String text) {
        return text.length() / 4;
    }
}