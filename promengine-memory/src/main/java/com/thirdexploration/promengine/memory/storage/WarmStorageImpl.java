package com.thirdexploration.promengine.memory.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.memory.config.MemoryProperties;
import com.thirdexploration.promengine.memory.exception.MemoryStorageException;
import com.thirdexploration.promengine.memory.model.StoredMemoryEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 温存储实现，基于 Parquet 列式存储和 JSONL 摘要文件。
 * 每个分区目录下可以包含多个 Parquet 文件（以 data-*.parquet 命名），
 * 以及一个统一的 summary.jsonl 摘要文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarmStorageImpl implements WarmStorage {

    private final MemoryProperties properties;
    private final ObjectMapper objectMapper;

    // 每个分区目录下的 Parquet 文件名前缀/后缀
    private static final String PARQUET_FILE_PREFIX = "data-";
    private static final String PARQUET_FILE_SUFFIX = ".parquet";
    private static final String SUMMARY_FILE_NAME = "summary.jsonl";

    // 缓存分区摘要数据，减少重复扫描
    private final Map<String, List<MemorySummary>> summaryCache = new ConcurrentHashMap<>();

    // Avro Schema 定义
    private static final Schema AVRO_SCHEMA = Schema.createRecord("MemoryRecord", null, null, false,
            List.of(
                    new Schema.Field("id", Schema.create(Schema.Type.STRING), null, (Object) null),
                    new Schema.Field("user_id", Schema.create(Schema.Type.STRING), null, (Object) null),
                    new Schema.Field("content", Schema.create(Schema.Type.STRING), null, (Object) null),
                    new Schema.Field("summary", Schema.create(Schema.Type.STRING), null, (Object) null),
                    new Schema.Field("timestamp", Schema.create(Schema.Type.LONG), null, (Object) null),
                    new Schema.Field("memory_type", Schema.create(Schema.Type.STRING), null, (Object) null),
                    new Schema.Field("importance", Schema.create(Schema.Type.FLOAT), null, (Object) null),
                    new Schema.Field("metadata", Schema.create(Schema.Type.STRING), null, (Object) null)
            ));

    private record MemorySummary(String id, String userId, long timestamp, String summary,
                                 String memoryType, float importance) {}

    // ---------- 公共接口实现 ----------

    @Override
    public void append(List<StoredMemoryEntry> records, String partitionMonth) {
        if (records.isEmpty()) return;
        try {
            Path partitionDir = getPartitionDir(partitionMonth);
            Files.createDirectories(Paths.get(partitionDir.toString()));

            // 生成一个新的 Parquet 文件（时间戳保证唯一）
            String fileName = PARQUET_FILE_PREFIX + System.currentTimeMillis() + PARQUET_FILE_SUFFIX;
            Path parquetPath = new Path(partitionDir, fileName);

            // 写入 Parquet（CREATE 模式）
            try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(parquetPath)
                    .withSchema(AVRO_SCHEMA)
                    .withCompressionCodec(CompressionCodecName.SNAPPY)
                    .build()) {
                for (StoredMemoryEntry rec : records) {
                    writer.write(toAvroRecord(rec));
                }
            }

            // 追加 JSONL 摘要文件
            Path summaryPath = new Path(partitionDir, SUMMARY_FILE_NAME);
            try (BufferedWriter summaryWriter = new BufferedWriter(
                    new FileWriter(summaryPath.toString(), true))) {
                for (StoredMemoryEntry rec : records) {
                    MemorySummary summary = new MemorySummary(
                            rec.getId(),
                            rec.getUserId(),
                            rec.getTimestamp().toEpochMilli(),
                            rec.getSummary() != null ? rec.getSummary() : truncate(rec.getContent(), 200),
                            rec.getType().name(),
                            rec.getImportance()
                    );
                    summaryWriter.write(objectMapper.writeValueAsString(summary));
                    summaryWriter.newLine();
                }
            }

            summaryCache.remove(partitionMonth);
            log.info("Appended {} records to warm partition {}, file {}", records.size(), partitionMonth, fileName);
        } catch (Exception e) {
            log.error("Failed to append records to warm storage partition {}", partitionMonth, e);
            throw new MemoryStorageException("Warm storage append failed", e);
        }
    }

    @Override
    public List<StoredMemoryEntry> queryByTimeRange(String userId, Instant from, Instant to, int limit) {
        List<String> partitions = getPartitionsInRange(from, to);
        List<StoredMemoryEntry> results = new ArrayList<>();

        for (String partition : partitions) {
            if (results.size() >= limit) break;
            List<MemorySummary> summaries = loadSummaries(partition);
            List<String> matchedIds = summaries.stream()
                    .filter(s -> s.userId().equals(userId) &&
                            s.timestamp() >= from.toEpochMilli() &&
                            s.timestamp() <= to.toEpochMilli())
                    .sorted(Comparator.comparingLong(MemorySummary::timestamp).reversed())
                    .limit(limit - results.size())
                    .map(MemorySummary::id)
                    .toList();

            if (!matchedIds.isEmpty()) {
                results.addAll(readFullRecordsByIdsInPartition(partition, matchedIds));
            }
        }
        return results;
    }

    @Override
    public List<StoredMemoryEntry> readFullRecordsByIds(List<String> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        List<StoredMemoryEntry> results = new ArrayList<>();
        for (String partition : listPartitions()) {
            List<MemorySummary> summaries = loadSummaries(partition);
            List<String> idsInPartition = summaries.stream()
                    .map(MemorySummary::id)
                    .filter(ids::contains)
                    .toList();
            if (!idsInPartition.isEmpty()) {
                results.addAll(readFullRecordsByIdsInPartition(partition, idsInPartition));
            }
        }
        return results;
    }

    @Override
    public int compact(String partitionMonth) {
        log.info("Starting compaction for partition {}", partitionMonth);
        try {
            // 读取该分区下所有记录
            List<StoredMemoryEntry> allRecords = readAllRecordsFromPartition(partitionMonth);
            if (allRecords.isEmpty()) return 0;

            Path partitionDir = getPartitionDir(partitionMonth);
            // 删除所有旧 Parquet 文件
            File dir = new File(partitionDir.toString());
            File[] oldFiles = dir.listFiles((d, name) -> name.startsWith(PARQUET_FILE_PREFIX) && name.endsWith(PARQUET_FILE_SUFFIX));
            if (oldFiles != null) {
                for (File f : oldFiles) {
                    Files.deleteIfExists(f.toPath());
                }
            }

            // 写入一个新的合并文件
            String newFileName = PARQUET_FILE_PREFIX + System.currentTimeMillis() + PARQUET_FILE_SUFFIX;
            Path newParquetPath = new Path(partitionDir, newFileName);
            try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(newParquetPath)
                    .withSchema(AVRO_SCHEMA)
                    .withCompressionCodec(CompressionCodecName.SNAPPY)
                    .build()) {
                for (StoredMemoryEntry rec : allRecords) {
                    writer.write(toAvroRecord(rec));
                }
            }

            // 重新生成摘要文件（覆盖写入）
            Path summaryPath = new Path(partitionDir, SUMMARY_FILE_NAME);
            try (BufferedWriter summaryWriter = new BufferedWriter(new FileWriter(summaryPath.toString(), false))) {
                for (StoredMemoryEntry rec : allRecords) {
                    MemorySummary summary = new MemorySummary(
                            rec.getId(),
                            rec.getUserId(),
                            rec.getTimestamp().toEpochMilli(),
                            rec.getSummary(),
                            rec.getType().name(),
                            rec.getImportance()
                    );
                    summaryWriter.write(objectMapper.writeValueAsString(summary));
                    summaryWriter.newLine();
                }
            }

            summaryCache.remove(partitionMonth);
            log.info("Compaction completed for partition {}, merged into single file {}", partitionMonth, newFileName);
            return allRecords.size();
        } catch (Exception e) {
            log.error("Compaction failed for partition {}", partitionMonth, e);
            throw new MemoryStorageException("Compaction failed", e);
        }
    }

    @Override
    public List<String> listPartitions() {
        java.nio.file.Path baseDir = Paths.get(properties.getDataDir(), "memory", "warm");
        if (!Files.exists(baseDir)) return Collections.emptyList();
        try (var stream = Files.list(baseDir)) {
            return stream.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.matches("\\d{4}-\\d{2}"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list partitions", e);
            return Collections.emptyList();
        }
    }

    @Override
    public long archiveToCold(String partitionMonth, ColdStorage coldStorage) {
        List<StoredMemoryEntry> records = readAllRecordsFromPartition(partitionMonth);
        if (records.isEmpty()) return 0;
        coldStorage.archive(records, partitionMonth);

        // 删除整个分区目录
        Path partitionDir = getPartitionDir(partitionMonth);
        try {
            Files.walk(Paths.get(partitionDir.toString()))
                    .sorted(Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
            summaryCache.remove(partitionMonth);
            log.info("Archived partition {} to cold storage and deleted warm data", partitionMonth);
        } catch (IOException e) {
            log.error("Failed to delete warm partition after archiving", e);
        }
        return records.size();
    }

    // ---------- 私有辅助方法 ----------

    private Path getPartitionDir(String partitionMonth) {
        return new Path(properties.getDataDir(), "memory/warm/" + partitionMonth);
    }

    private List<String> getPartitionsInRange(Instant from, Instant to) {
        List<String> partitions = new ArrayList<>();
        java.time.YearMonth ymFrom = java.time.YearMonth.from(from.atZone(java.time.ZoneId.systemDefault()));
        java.time.YearMonth ymTo = java.time.YearMonth.from(to.atZone(java.time.ZoneId.systemDefault()));
        while (!ymFrom.isAfter(ymTo)) {
            partitions.add(ymFrom.toString());
            ymFrom = ymFrom.plusMonths(1);
        }
        return partitions;
    }

    private List<MemorySummary> loadSummaries(String partitionMonth) {
        return summaryCache.computeIfAbsent(partitionMonth, p -> {
            List<MemorySummary> summaries = new ArrayList<>();
            Path summaryPath = new Path(getPartitionDir(p), SUMMARY_FILE_NAME);
            if (!Files.exists(Paths.get(summaryPath.toString()))) return summaries;
            try (BufferedReader reader = new BufferedReader(new FileReader(summaryPath.toString()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    summaries.add(objectMapper.readValue(line, MemorySummary.class));
                }
            } catch (IOException e) {
                log.warn("Failed to load summaries for partition {}", p, e);
            }
            return summaries;
        });
    }

    private List<StoredMemoryEntry> readAllRecordsFromPartition(String partitionMonth) {
        List<StoredMemoryEntry> records = new ArrayList<>();
        Path partitionDir = getPartitionDir(partitionMonth);
        File dir = new File(partitionDir.toString());
        File[] parquetFiles = dir.listFiles((d, name) -> name.startsWith(PARQUET_FILE_PREFIX) && name.endsWith(PARQUET_FILE_SUFFIX));
        if (parquetFiles == null) return records;

        for (File file : parquetFiles) {
            try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(new Path(file.getAbsolutePath())).build()) {
                GenericRecord record;
                while ((record = reader.read()) != null) {
                    records.add(fromAvroRecord(record));
                }
            } catch (IOException e) {
                log.error("Failed to read parquet file {}", file, e);
            }
        }
        return records;
    }

    private List<StoredMemoryEntry> readFullRecordsByIdsInPartition(String partitionMonth, List<String> ids) {
        Set<String> idSet = new HashSet<>(ids);
        List<StoredMemoryEntry> results = new ArrayList<>();
        Path partitionDir = getPartitionDir(partitionMonth);
        File dir = new File(partitionDir.toString());
        File[] parquetFiles = dir.listFiles((d, name) -> name.startsWith(PARQUET_FILE_PREFIX) && name.endsWith(PARQUET_FILE_SUFFIX));
        if (parquetFiles == null) return results;

        for (File file : parquetFiles) {
            try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(new Path(file.getAbsolutePath())).build()) {
                GenericRecord record;
                while ((record = reader.read()) != null) {
                    String id = record.get("id").toString();
                    if (idSet.contains(id)) {
                        results.add(fromAvroRecord(record));
                        idSet.remove(id);
                        if (idSet.isEmpty()) break;
                    }
                }
            } catch (IOException e) {
                log.error("Failed to read parquet file {}", file, e);
            }
            if (idSet.isEmpty()) break;
        }
        return results;
    }

    private GenericRecord toAvroRecord(StoredMemoryEntry entry) throws IOException {
        GenericRecord record = new GenericData.Record(AVRO_SCHEMA);
        record.put("id", entry.getId());
        record.put("user_id", entry.getUserId());
        record.put("content", entry.getContent());
        record.put("summary", entry.getSummary() != null ? entry.getSummary() : "");
        record.put("timestamp", entry.getTimestamp().toEpochMilli());
        record.put("memory_type", entry.getType().name());
        record.put("importance", entry.getImportance());
        record.put("metadata", objectMapper.writeValueAsString(entry.getMetadata()));
        return record;
    }

    private StoredMemoryEntry fromAvroRecord(GenericRecord record) {
        try {
            Map<String, Object> metadata = objectMapper.readValue(
                    record.get("metadata").toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            return StoredMemoryEntry.builder()
                    .id(record.get("id").toString())
                    .userId(record.get("user_id").toString())
                    .content(record.get("content").toString())
                    .summary(record.get("summary").toString())
                    .timestamp(Instant.ofEpochMilli((Long) record.get("timestamp")))
                    .type(com.thirdexploration.promengine.core.domain.MemoryEntry.MemoryType.valueOf(
                            record.get("memory_type").toString()))
                    .importance((Float) record.get("importance"))
                    .metadata(metadata)
                    .storageTier("WARM")
                    .parquetPath(record.toString()) // 简化
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert Avro record", e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}