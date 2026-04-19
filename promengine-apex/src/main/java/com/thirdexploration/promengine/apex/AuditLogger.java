package com.thirdexploration.promengine.apex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.ApexController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogger {

    private final ObjectMapper objectMapper;
    private final ApexProperties properties;

    public void log(String userId, ApexController.UsageRecord record) {
        if (!properties.getAudit().isEnabled()) return;
        try {
            Map<String, Object> entry = Map.of(
                    "timestamp", Instant.now().toString(),
                    "user_id", userId,
                    "model", record.getModel(),
                    "provider", record.getProvider(),
                    "prompt_tokens", record.getPromptTokens(),
                    "completion_tokens", record.getCompletionTokens(),
                    "cost", record.getCost(),
                    "latency_ms", record.getLatencyMs(),
                    "status", record.getStatus()
            );
            String line = objectMapper.writeValueAsString(entry) + "\n";
            Path auditFile = Path.of(properties.getAudit().getPath(), "api-audit.jsonl");
            Files.createDirectories(auditFile.getParent());
            Files.write(auditFile, line.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("Failed to write API audit log", e);
        }
    }
}