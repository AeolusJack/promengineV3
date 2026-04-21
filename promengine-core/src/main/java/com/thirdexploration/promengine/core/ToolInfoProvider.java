package com.thirdexploration.promengine.core;

import java.util.List;

public interface ToolInfoProvider {

    List<ToolInfo> getAvailableTools();

    record ToolInfo(String name, String description) {}
    /**
     * 获取所有可用工具的名称列表。
     */
    List<String> getAvailableToolNames();
    /** 获取所有可用工具的详细描述（用于注入提示词） */
    String getToolDescriptions();
}