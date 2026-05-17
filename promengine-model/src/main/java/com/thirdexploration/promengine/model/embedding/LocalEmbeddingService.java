package com.thirdexploration.promengine.model.embedding;

import com.thirdexploration.promengine.core.embedding.EmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class LocalEmbeddingService implements EmbeddingService {
    private final EmbeddingModel embeddingModel;

    public LocalEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) return new float[0];
        return embeddingModel.embed(text);
    }
}