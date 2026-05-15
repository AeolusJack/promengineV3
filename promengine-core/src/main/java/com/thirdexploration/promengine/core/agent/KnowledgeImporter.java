package com.thirdexploration.promengine.core.agent;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 知识注入器接口，用于批量导入领域知识。
 */
public interface KnowledgeImporter {
    void importBatch(List<KnowledgeItem> items);

    @Data
    @Builder
    class KnowledgeItem {
        private String content;
        private Map<String, Object> metadata;
    }
}