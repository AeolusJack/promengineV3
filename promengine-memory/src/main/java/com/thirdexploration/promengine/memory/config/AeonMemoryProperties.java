package com.thirdexploration.promengine.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "aeon.memory")
public class AeonMemoryProperties {

    private String metaPolicyPath = "./config/meta-policies.json";

    private Retrieval retrieval = new Retrieval();
    private Evolution evolution = new Evolution();
    private Governance governance = new Governance();
    private VectorConfig vector = new VectorConfig();

    private int maxMemoryChars = 4000;
    private int maxPerMemoryChars = 200;

    // ========== 深度检索与路由配置（新增） ==========
    private boolean deepRetrievalEnabled = true;
    private boolean graphExpansionEnabled = false;
    private int graphMaxDepth = 2;
    private int graphMaxResults = 20;
    private double complexityLowThreshold = 0.3;
    private double complexityHighThreshold = 0.7;

    @Data
    public static class VectorConfig {
        private String engine = "chroma";
        private int dimension = 768;
        private String milvusHost = "localhost";
        private int milvusPort = 19530;
    }

    @Data
    public static class Retrieval {
        private boolean deduplicationEnabled = true;
        private double verifiedWeightBoost = 1.5;
        private int defaultTopK = 10;
        private List<EnhancementPipeline> enhancementPipelines = new ArrayList<>();
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
        private boolean decayEnabled = true;
        private String decayCron = "0 0 3 * * ?";
        private Tame tame = new Tame();
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