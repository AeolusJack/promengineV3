package com.thirdexploration.promengine.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityProxyService {

    private final IdentityProxyProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public void sendProactiveMessage(String userId, String templateId, Map<String, String> variables) {
        if (!properties.isEnabled()) {
            log.debug("Identity proxy is disabled");
            return;
        }
        // 检查规则引擎条件
        log.info("Sending proactive message to {} using template {}", userId, templateId);
        // 实际调用平台适配器发送
    }
}