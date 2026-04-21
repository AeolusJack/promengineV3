package com.thirdexploration.promengine.prompt.compression;

/**
 * 提示词压缩器接口。
 */
public interface PromptCompressor {
    String compress(String prompt, int targetTokens);
}