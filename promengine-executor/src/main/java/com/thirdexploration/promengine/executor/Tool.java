package com.thirdexploration.promengine.executor;

import java.util.Map;

public interface Tool {
    String getName();
    String getDescription();
    Map<String, Object> execute(Map<String, Object> params);
}