package com.thirdexploration.promengine.core;

import com.thirdexploration.promengine.core.domain.RenderedPrompt;
import com.thirdexploration.promengine.core.domain.TaskContext;
import com.thirdexploration.promengine.core.domain.Template;

/**
 * 提示词管理子系统接口。
 */
public interface PromptManager {

    /**
     * 根据任务上下文渲染最终提示词。
     */
    RenderedPrompt render(TaskContext ctx);

    /**
     * 注册模板。
     */
    void registerTemplate(Template template);

    /**
     * 记录用户反馈，用于模板优化。
     */
    void recordFeedback(String templateId, String version, Feedback feedback);

    interface Feedback {
        double getRating();          // 1-5 分
        String getComment();
        boolean isAccepted();
    }

    /** 新增：压缩提示词 */
    String compress(String prompt);


}