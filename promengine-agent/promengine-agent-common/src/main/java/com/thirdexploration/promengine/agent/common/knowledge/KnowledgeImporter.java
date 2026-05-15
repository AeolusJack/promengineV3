package com.thirdexploration.promengine.agent.common.knowledge;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

public interface KnowledgeImporter {
    void importBatch(List<KnowledgeItem> items);
    
    @Data
    @Builder
    class KnowledgeItem {
        private String content;
        private Map<String, Object> metadata; // 可包含 userId, domain, projectId 等
    }
}