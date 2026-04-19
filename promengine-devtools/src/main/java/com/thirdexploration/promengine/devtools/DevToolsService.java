package com.thirdexploration.promengine.devtools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevToolsService {

    private final DevToolsProperties properties;

    public boolean isDebugTraceEnabled() {
        return properties.isDebugTraceEnabled();
    }

    public void exportTrace(String sessionId) {
        if (!properties.isDebugTraceEnabled()) return;
        // 导出 .promtrace 文件
        log.info("Exporting trace for session {}", sessionId);
    }
}