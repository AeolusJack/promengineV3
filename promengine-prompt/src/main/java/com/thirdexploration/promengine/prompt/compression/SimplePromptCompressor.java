package com.thirdexploration.promengine.prompt.compression;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "promengine.prompt.compression.engine", havingValue = "simple", matchIfMissing = true)
public class SimplePromptCompressor implements PromptCompressor {

    @Override
    public String compress(String prompt, int targetTokens) {
        int currentTokens = prompt.length() / 4;
        if (currentTokens <= targetTokens) return prompt;
        int maxChars = targetTokens * 4;
        return prompt.substring(0, maxChars) + "\n[内容已压缩]";
    }
}