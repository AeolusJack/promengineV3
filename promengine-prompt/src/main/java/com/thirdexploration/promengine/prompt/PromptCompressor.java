package com.thirdexploration.promengine.prompt;

import org.springframework.stereotype.Component;

@Component
public class PromptCompressor {

    public String compress(String prompt, int targetTokens) {
        int currentTokens = prompt.length() / 4;
        if (currentTokens <= targetTokens) return prompt;

        // 简单截断，实际可调用摘要模型
        int maxChars = targetTokens * 4;
        return prompt.substring(0, maxChars) + "\n[内容已压缩]";
    }
}