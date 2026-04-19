package com.thirdexploration.promengine.memory.service;

/**
 * 嵌入服务接口，用于生成文本向量。
 */
public interface EmbeddingService {
    float[] embed(String text);
}