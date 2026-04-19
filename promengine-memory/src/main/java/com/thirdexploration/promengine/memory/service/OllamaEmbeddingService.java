package com.thirdexploration.promengine.memory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    // 嵌入模型单次最大输入字符数（保守值，可根据实际模型调整）
    private static final int MAX_CHARS_PER_CHUNK = 2000;
    // 分块重叠字符数，避免边界语义丢失
    private static final int CHUNK_OVERLAP = 200;

    @Override
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            return new float[0];
        }

        // 若文本长度在限制内，直接调用
        if (text.length() <= MAX_CHARS_PER_CHUNK) {
            return callEmbeddingApi(text);
        }

        // 超长文本：分块嵌入后聚合
        log.info("Text length {} exceeds limit, splitting into chunks", text.length());
        List<float[]> chunkVectors = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHARS_PER_CHUNK, text.length());
            String chunk = text.substring(start, end);
            chunkVectors.add(callEmbeddingApi(chunk));
            start = end - CHUNK_OVERLAP;
            if (start >= text.length()) break;
        }

        // 聚合：对每个维度取平均值
        int dim = chunkVectors.get(0).length;
        float[] aggregated = new float[dim];
        for (float[] vec : chunkVectors) {
            for (int i = 0; i < dim; i++) {
                aggregated[i] += vec[i];
            }
        }
        for (int i = 0; i < dim; i++) {
            aggregated[i] /= chunkVectors.size();
        }
        log.debug("Aggregated embedding from {} chunks", chunkVectors.size());
        return aggregated;
    }

    private float[] callEmbeddingApi(String text) {
        return embeddingModel.embed(text);
    }
}