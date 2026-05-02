package com.thirdexploration.promengine.memory.governance;

import com.thirdexploration.promengine.memory.model.MemoryRecord;
import com.thirdexploration.promengine.memory.model.Provenance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ProceduralMemoryGate {

    // 操作步骤关键词/模式
    private static final List<String> PROCEDURAL_INDICATORS = List.of(
            "步骤", "执行", "调用", "操作", "流程", "方法", "→",
            "step", "execute", "call", "procedure", "function",
            "首先", "然后", "接着", "最后", "下一步",
            "```", "`calc", "`read_file", "`write_file"
    );

    private static final Pattern STEP_PATTERN = Pattern.compile(
            "(\\d+[.、)]\\s*.+)|(第[一二三四五]步)|(step\\s*\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 评估记忆是否适合转化为过程记忆。
     * @return true 表示允许转化
     */
    public boolean evaluate(MemoryRecord record) {
        if (record == null) return false;

        // 1. 来源检查
        Provenance provenance = record.getProvenance();
        String source = provenance != null ? provenance.getSource() : "unknown";

        boolean sourcePassed = "tool_output".equals(source) || "agent_generated".equals(source);
        if (!sourcePassed) {
            log.debug("Memory {} rejected: source={}", record.getId(), source);
            return false;
        }

        // 2. 内容模式检查
        String content = record.getContent() != null ? record.getContent().toLowerCase() : "";
        String summary = record.getSummary() != null ? record.getSummary().toLowerCase() : "";
        String combined = content + " " + summary;

        boolean hasProceduralPattern = PROCEDURAL_INDICATORS.stream().anyMatch(combined::contains)
                || STEP_PATTERN.matcher(combined).find();

        if (!hasProceduralPattern) {
            log.debug("Memory {} rejected: no procedural pattern found", record.getId());
            return false;
        }

        // 3. 效用评分检查
        if (record.getUtilityScore() < 0.7) {
            log.debug("Memory {} rejected: utilityScore={}", record.getId(), record.getUtilityScore());
            return false;
        }

        // 4. 安全评分检查
        if (record.getSafetyScore() < 0.5) {
            log.debug("Memory {} rejected: safetyScore={}", record.getId(), record.getSafetyScore());
            return false;
        }

        // 5. 已验证加分（可选，已验证自动通过效用门槛降低到 0.5）
        if (provenance != null && provenance.isVerified()) {
            return true; // 已验证记忆放宽标准，只要来源和模式通过即可
        }

        log.info("Memory {} passed procedural gate", record.getId());
        return true;
    }
}