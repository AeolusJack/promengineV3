package com.thirdexploration.promengine.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 记忆模块配置属性，对应 application.yml 中的 promengine.memory 节点。
 */
@Data
@Component
@ConfigurationProperties(prefix = "promengine.memory")
public class MemoryProperties {

    private String backend = "tiered";
    private Duration hotRetention = Duration.ofDays(30);
    private Duration warmRetention = Duration.ofDays(90);

    private WarmStorageConfig warmStorage = new WarmStorageConfig();
    private ColdStorageConfig coldStorage = new ColdStorageConfig();
    private RetrievalConfig retrieval = new RetrievalConfig();
    private WarmupConfig warmup = new WarmupConfig();
    private ExportConfig export = new ExportConfig();
    private ForgettingConfig forgetting = new ForgettingConfig();
    private VectorConfig vector = new VectorConfig();
    private GraphConfig graph = new GraphConfig();
    /**
     * 记忆模块数据根目录，默认为 ./data
     */
    private String dataDir = "./data";
    @Data
    public static class WarmStorageConfig {
        private String partitionBy = "month";
        private CompactionConfig compaction = new CompactionConfig();
        private String summaryStrategy = "jsonl_summary";

        @Data
        public static class CompactionConfig {
            private boolean enabled = true;
            private String schedule = "0 3 1 * *";
            private String minFileSizeToMerge = "100MB";
        }
    }
    private MilvusConfig milvus = new MilvusConfig();

    @Data
    public static class MilvusConfig {
        private String host = "localhost";
        private int port = 19530;
        private String database = "default";
    }
    @Data
    public static class ColdStorageConfig {
        private String archivePartitionBy = "year";
    }

    @Data
    public static class RetrievalConfig {
        private Duration defaultTimeWindow = Duration.ofDays(30);
        private boolean allowColdScanByDefault = false;
    }

    @Data
    public static class WarmupConfig {
        private boolean async = true;
        private boolean createSnapshot = true;
        private Duration snapshotInterval = Duration.ofDays(1);
        private boolean progressTracking = true;
    }

    @Data
    public static class ExportConfig {
        private String checksumAlgorithm = "SHA-256";
        private boolean merkleTreeEnabled = true;
        private String splitSize = "4GB";
    }

    @Data
    public static class ForgettingConfig {
        private Duration softDeletePeriod = Duration.ofDays(30);
        private boolean autoWeatheringEnabled = false;
    }

    @Data
    public static class VectorConfig {
        private String engine = "chroma";
        private String fallback = "jvector";
        private Integer dimension = 768;   // 新增：向量维度
    }

    @Data
    public static class GraphConfig {
        private boolean enabled = false;
        private String backend = "falkordb";
    }
}