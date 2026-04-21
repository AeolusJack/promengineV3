package com.thirdexploration.promengine.prompt.core;

import com.thirdexploration.promengine.core.domain.TaskContext;

/**
 * 上下文构建器接口，将构建逻辑进一步解耦，便于替换实现。
 */
public interface PromptContextBuilder {
    PromptContext build(TaskContext ctx);
}