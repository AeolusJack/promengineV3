package com.thirdexploration.promengine.model.util;

public class TokenCounter {
    // 简单估算，实际应集成 tiktoken 或类似库
    public static int estimate(String text) {
        return text.length() / 4;
    }
}