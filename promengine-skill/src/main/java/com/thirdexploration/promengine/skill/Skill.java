package com.thirdexploration.promengine.skill;

import java.util.Map;

public interface Skill {
    String getName();
    String getDescription();
    Map<String, Object> execute(Map<String, Object> input);
    default void onLoad() {}
    default void onUnload() {}
}