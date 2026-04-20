package com.thirdexploration.promengine.core;

import java.util.List;

public interface ToolInfoProvider {

    List<ToolInfo> getAvailableTools();

    record ToolInfo(String name, String description) {}
}