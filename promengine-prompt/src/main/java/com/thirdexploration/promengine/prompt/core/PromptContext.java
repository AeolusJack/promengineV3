package com.thirdexploration.promengine.prompt.core;

import com.thirdexploration.promengine.memory.model.MemoryEntry;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 提示词上下文对象，包含模型在生成回复时需要的所有信息。
 * 这是 Context Engineering 中的核心载体。
 */
@Data
@Builder
public class PromptContext {
    /** 用户ID */
    private String userId;
    /** 会话ID */
    private String sessionId;
    /** 用户当前输入 */
    private String userInput;
    /** 任务类型，用于选择模板 */
    private String taskType;
    /** 认知状态（专注度、燃料等） */
    private Map<String, Object> cognitiveState;
    /** 检索到的相关记忆 */
    private List<MemoryEntry> memories;
    /** 可用工具名称列表 */
    private List<String> availableTools;
    /** 可用工具的详细描述（用于注入提示词） */
    private String toolDescriptions;
    /** 扩展变量 */
    private Map<String, Object> extraVariables;
}