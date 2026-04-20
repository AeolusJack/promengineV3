package com.thirdexploration.promengine.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.memory.retrieval-policy")
public class MemoryRetrievalPolicyProperties {

    private boolean hotEnabled = true;
    private boolean warmEnabled = true;
    private boolean luceneEnabled = true;
    private boolean vectorEnabled = true;

    private int hotTopK = 5;
    private int warmTopK = 5;
    private int luceneTopK = 5;
    private int vectorTopK = 5;
    private int fusionTopK = 10;

    // 各通路在 RRF 融合前的权重系数（默认为 1.0）
    private double hotWeight = 1.0;
    private double warmWeight = 1.0;
    private double luceneWeight = 1.0;
    private double vectorWeight = 1.0;
    /** 注入 Prompt 的记忆内容最大字符数（0 表示不限制） */
    private int maxMemoryChars = 4000;

    /** 单条记忆最大显示字符数（超过则截断加省略号） */
    private int maxPerMemoryChars = 200;
    // 根据任务复杂度动态调整的配置（可选）
    private Map<String, ComplexityLevelPolicy> complexityOverrides;

    @Data
    public static class ComplexityLevelPolicy {
        private boolean hotEnabled;
        private boolean warmEnabled;
        private boolean luceneEnabled;
        private boolean vectorEnabled;
        private Integer hotTopK;
        private Integer warmTopK;
        private Integer luceneTopK;
        private Integer vectorTopK;
        private Integer fusionTopK;
    }
}