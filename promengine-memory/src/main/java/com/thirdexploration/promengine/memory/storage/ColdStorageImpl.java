package com.thirdexploration.promengine.memory.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.memory.config.MemoryProperties;
import com.thirdexploration.promengine.memory.exception.MemoryStorageException;
import com.thirdexploration.promengine.memory.model.StoredMemoryEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Component
public class ColdStorageImpl implements ColdStorage {

    private final MemoryProperties properties;
    private final ObjectMapper objectMapper;
    private final String dataDir;

    public ColdStorageImpl(MemoryProperties properties,
                           ObjectMapper objectMapper,
                           @Value("${promengine.data-dir:./data}") String dataDir) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.dataDir = dataDir;
    }

    @Override
    public void archive(List<StoredMemoryEntry> records, String archiveId) {
        Path archiveFile = getArchivePath(archiveId);
        try {
            Files.createDirectories(archiveFile.getParent());
            boolean exists = Files.exists(archiveFile);
            try (OutputStream fos = Files.newOutputStream(archiveFile,
                    exists ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
                 GZIPOutputStream gzip = new GZIPOutputStream(fos);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(gzip))) {
                for (StoredMemoryEntry rec : records) {
                    writer.write(objectMapper.writeValueAsString(rec));
                    writer.newLine();
                }
            }
            log.info("Archived {} records to {}", records.size(), archiveId);
        } catch (IOException e) {
            log.error("Failed to archive records", e);
            throw new MemoryStorageException("Cold storage archive failed", e);
        }
    }

    @Override
    public List<StoredMemoryEntry> retrieveByIds(List<String> ids) {
        List<StoredMemoryEntry> results = new ArrayList<>();
        for (String archiveId : listArchives()) {
            try (InputStream fis = Files.newInputStream(getArchivePath(archiveId));
                 GZIPInputStream gzip = new GZIPInputStream(fis);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(gzip))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    StoredMemoryEntry entry = objectMapper.readValue(line, StoredMemoryEntry.class);
                    if (ids.contains(entry.getId())) {
                        results.add(entry);
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to read archive {}", archiveId, e);
            }
        }
        return results;
    }

    @Override
    public List<String> listArchives() {
        Path baseDir = Paths.get(dataDir, "memory", "cold");
        if (!Files.exists(baseDir)) return List.of();
        try (var stream = Files.list(baseDir)) {
            return stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("archive-") && name.endsWith(".jsonl.gz"))
                    .map(name -> name.substring(8, name.length() - 8))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list cold archives", e);
            return List.of();
        }
    }

    private Path getArchivePath(String archiveId) {
        return Paths.get(dataDir, "memory", "cold", "archive-" + archiveId + ".jsonl.gz");
    }
}