package com.thirdexploration.promengine.verifier;

import com.thirdexploration.promengine.verifier.model.IntentStructure;
import com.thirdexploration.promengine.verifier.model.VerificationResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 基于 YAML 配置的意图过滤器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentFilterEngine {

    private final VerifierProperties properties;
    private List<FilterRule> rules;

    @PostConstruct
    public void loadRules() {
        if (!properties.isIntentFiltersEnabled()) return;
        try (InputStream is = getClass().getResourceAsStream("/intent-filters.yaml")) {
            if (is == null) {
                log.warn("No intent-filters.yaml found, using empty rules");
                rules = List.of();
                return;
            }
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(is);
            List<Map<String, Object>> rawRules = (List<Map<String, Object>>) config.get("intent-filters");
            rules = rawRules.stream().map(this::parseRule).toList();
            log.info("Loaded {} intent filter rules", rules.size());
        } catch (Exception e) {
            log.error("Failed to load intent filters", e);
            rules = List.of();
        }
    }

    private FilterRule parseRule(Map<String, Object> map) {
        return new FilterRule(
                (String) map.get("name"),
                (String) map.get("condition"),
                (String) map.get("action")
        );
    }

    public VerificationResult evaluate(IntentStructure intent) {
        for (FilterRule rule : rules) {
            if (matches(intent, rule.condition)) {
                if ("BLOCK".equals(rule.action)) {
                    return VerificationResult.blocked(rule.name);
                }
            }
        }
        return VerificationResult.passed();
    }

    private boolean matches(IntentStructure intent, String condition) {
        // 简单实现：解析类似 "action == 'transfer' AND amount > user.daily_limit"
        // 实际应使用表达式引擎（如 MVEL 或 SpEL）
        if (condition.contains("transfer")) {
            return "transfer".equals(intent.getAction());
        }
        return false;
    }

    private record FilterRule(String name, String condition, String action) {}
}