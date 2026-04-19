package com.thirdexploration.promengine.ethics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.core.domain.TaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogger {

    private final ObjectMapper objectMapper;
    private final EthicsProperties properties;

    @Async
    public void log(TaskContext ctx, EthicalGovernor.EthicalDecision decision) {
        if (!properties.isAuditEnabled()) return;
        try {
            AuditEntry entry = AuditEntry.builder()
                    .timestamp(Instant.now())
                    .userId(ctx.getUserId())
                    .input(ctx.getUserInput().getText())
                    .decision(decision.name())
                    .build();
            String line = objectMapper.writeValueAsString(entry) + "\n";
            Path auditFile = Path.of(properties.getAuditPath(), "ethics-audit.jsonl");
            Files.createDirectories(auditFile.getParent());
            Files.write(auditFile, line.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("Failed to write ethics audit log", e);
        }
    }

    @lombok.Builder
    private record AuditEntry(Instant timestamp, String userId, String input, String decision) {}
}