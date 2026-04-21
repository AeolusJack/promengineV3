package com.thirdexploration.promengine.prompt;

import com.thirdexploration.promengine.core.PromptManager;
import com.thirdexploration.promengine.core.domain.RenderedPrompt;
import com.thirdexploration.promengine.core.domain.TaskContext;
import com.thirdexploration.promengine.prompt.compression.PromptCompressor;
import com.thirdexploration.promengine.prompt.config.PromptProperties;
import com.thirdexploration.promengine.prompt.core.PromptContextBuilder;
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
//    private final PromptContextBuilder contextBuilder;  // 移除 contextBuilder 依赖，回归原本职责
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

        // 注意：此处需要变量，但构建变量的逻辑已移到管线中，
        // 如果仍然有外部直接调用 PromptManager.render()，需要外部传入变量。
        // 为了兼容，可以从 ctx.getVariables() 获取变量。
        Map<String, Object> variables = ctx.getVariables();

        String rendered = renderEngine.render(template, variables);

        if (properties.getCompression().isEnabled()) {
            rendered = compressor.compress(rendered, properties.getCompression().getTargetMaxTokens());
        }

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


    public String compress(String prompt) {
        if (!properties.getCompression().isEnabled()) {
            return prompt;
        }
        return compressor.compress(prompt, properties.getCompression().getTargetMaxTokens());
    }

    private int estimateTokens(String text) {
        return text.length() / 4;
    }
}