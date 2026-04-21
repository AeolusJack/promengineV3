package com.thirdexploration.promengine.prompt.core;

import com.thirdexploration.promengine.core.domain.TaskContext;

/**
 * 提示词管线接口，定义上下文收集、渲染、压缩的标准流程。
 */
public interface PromptPipeline {
    /**
     * 从任务上下文收集所有必要信息，构建 PromptContext。
     */
    PromptContext collect(TaskContext ctx);

    /**
     * 将 PromptContext 渲染为最终的提示词字符串。
     */
    String render(PromptContext context);

    /**
     * 对渲染后的提示词进行压缩（如果启用）。
     */
    String compress(String prompt);
}