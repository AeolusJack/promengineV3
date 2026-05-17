package com.thirdexploration.promengine.model.embedding;

import org.springframework.stereotype.Component;

@Component
public class FakeEmbeddingService implements com.thirdexploration.promengine.core.embedding.EmbeddingService {
    @Override
    public float[] embed(String text) {
        // 简单 hash 模拟，实际只需保证相同文本生成相同向量
        int hash = text.hashCode();
        float[] vec = new float[4];
        for (int i = 0; i < 4; i++) {
            vec[i] = (hash >> (i * 8)) & 0xFF;
        }
        return vec;
    }
}