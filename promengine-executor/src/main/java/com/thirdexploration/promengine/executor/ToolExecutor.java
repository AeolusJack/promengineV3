package com.thirdexploration.promengine.executor;


import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public interface ToolExecutor {
    /**
     * 执行工具调用，返回字符串结果。
     */
    String execute(AssistantMessage.ToolCall toolCall);

    /**
     * 获取可供模型调用的工具回调列表（Spring AI M7 风格）。
     */
    List<ToolCallback> getAvailableTools();

    /**
     * 获取工具的自然语言描述，用于拼接到系统提示词中。
     */
    String getToolDescriptions();

    List<String> getAvailableToolNames();


}