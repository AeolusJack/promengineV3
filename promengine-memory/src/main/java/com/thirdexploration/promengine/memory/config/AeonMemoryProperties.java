package com.thirdexploration.promengine.memory.config;

import io.milvus.v2.service.collection.request.CreateCollectionReq;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Aeon 记忆系统配置属性。
 * 注意：域、层级、共享级别等元策略已移至独立的 JSON 文件管理，
 * 此处仅保留与元策略无关的系统级配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aeon.memory")
public class AeonMemoryProperties {

    /**
     * 元策略配置文件路径
     */
    private String metaPolicyPath = "./config/meta-policies.json";

    /**
     * 检索相关配置
     */
    private Retrieval retrieval = new Retrieval();

    /**
     * 进化相关配置
     */
    private Evolution evolution = new Evolution();

    /**
     * 治理相关配置
     */
    private Governance governance = new Governance();


    /**
     * 向量存储相关配置
     */
    private VectorConfig vector = new VectorConfig();

    /** 注入 Prompt 的记忆内容最大字符数（0 表示不限制） */
    private int maxMemoryChars = 4000;

    /** 单条记忆最大显示字符数（超过则截断加省略号） */
    private int maxPerMemoryChars = 200;

    @Data
    public static class VectorConfig {
        private String engine = "chroma";
        private int dimension = 768;
        private String milvusHost = "localhost";
        private int milvusPort = 19530;
    }

    @Data
    public static class Retrieval {
        /**
         * 是否启用写入去重
         */
        private boolean deduplicationEnabled = true;

        /**
         * 已验证记忆的权重加成系数
         */
        private double verifiedWeightBoost = 1.5;

        /**
         * 默认返回结果数量
         */
        private int defaultTopK = 10;

        /**
         * 增强管线配置
         */
        private List<EnhancementPipeline> enhancementPipelines = new ArrayList<>();

        /**
         * 跨域融合配置
         */
        private CrossDomain crossDomain = new CrossDomain();

        @Data
        public static class EnhancementPipeline {
            private String name;
            private List<Step> steps = new ArrayList<>();

            @Data
            public static class Step {
                private String type;
                private boolean llmEnabled = false;
                private String model = "gemma4-mini";
            }
        }

        @Data
        public static class CrossDomain {
            private List<String> strategies = List.of("parallel", "augment", "bridge");
        }
    }

    @Data
    public static class Evolution {
        /**
         * 是否启用遗忘曲线衰减
         */
        private boolean decayEnabled = true;

        /**
         * 衰减任务 Cron 表达式
         */
        private String decayCron = "0 0 3 * * ?";

        /**
         * TAME 双轨进化配置
         */
        private Tame tame = new Tame();

        /**
         * MemEvolve 元进化配置
         */
        private Memevolve memevolve = new Memevolve();

        @Data
        public static class Tame {
            private boolean enabled = true;
            private int evaluatorMemorySize = 10000;
        }

        @Data
        public static class Memevolve {
            private boolean enabled = false;
            private String schedule = "0 0 3 * * SUN";
        }
    }

    @Data
    public static class Governance {
        private boolean enabled = true;
        private Audit audit = new Audit();

        @Data
        public static class Audit {
            private boolean enabled = true;
            private String logPath = "./data/audit/memory";
        }
    }
}