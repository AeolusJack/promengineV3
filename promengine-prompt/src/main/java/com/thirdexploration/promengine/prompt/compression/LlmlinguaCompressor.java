package com.thirdexploration.promengine.prompt.compression;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 预留的 LLMLingua-2 压缩器实现。
 * 当配置 promengine.prompt.compression.engine=llmlingua2 时激活。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "promengine.prompt.compression.engine", havingValue = "llmlingua2")
public class LlmlinguaCompressor implements PromptCompressor {

    // TODO: 集成 LLMLingua-2 依赖，实现真正的压缩逻辑
    @Override
    public String compress(String prompt, int targetTokens) {
        log.warn("LLMLingua-2 compressor is not yet implemented. Falling back to simple truncation.");
        int currentTokens = prompt.length() / 4;
        if (currentTokens <= targetTokens) return prompt;
        int maxChars = targetTokens * 4;
        return prompt.substring(0, maxChars) + "\n[内容已压缩]";
    }
}