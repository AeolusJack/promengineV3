package com.thirdexploration.promengine.prompt.window;

import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.prompt.core.PromptContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ContextWindowManager {

    private static final int MAX_MEMORY_CHARS = 4000;   // 记忆部分最大字符
    private static final int MAX_PER_MEMORY_CHARS = 200; // 单条记忆摘要最大字符
    private static final int MAX_TOOL_CHARS = 1000;      // 工具描述最大字符

    /**
     * 裁剪记忆列表，按重要性+强度排序，并截断内容
     */
    public List<MemoryEntry> trimMemories(List<MemoryEntry> memories, int maxChars) {
        if (memories == null || memories.isEmpty()) return List.of();

        List<MemoryEntry> sorted = memories.stream()
                .sorted(Comparator.comparingDouble(m -> 
                    ((m.getImportance()) * 0.5 + (m.getStrength() * 0.5))))
                .collect(Collectors.toList());

        List<MemoryEntry> trimmed = new java.util.ArrayList<>();
        int totalChars = 0;
        for (MemoryEntry mem : sorted) {
            String summary = mem.getSummary();
            if (summary == null || summary.isBlank()) {
                summary = truncate(mem.getContent(), MAX_PER_MEMORY_CHARS);
            } else {
                summary = truncate(summary, MAX_PER_MEMORY_CHARS);
            }
            // 创建新条目以不改变原对象，只保留摘要和域信息
            MemoryEntry entry = MemoryEntry.builder()
                    .id(mem.getId())
                    .summary(summary)
                    .domain(mem.getDomain())
                    .strength(mem.getStrength())
                    .importance(mem.getImportance())
                    .build();

            int chars = summary.length();
            if (totalChars + chars > maxChars) {
                break;
            }
            trimmed.add(entry);
            totalChars += chars;
        }
        return trimmed;
    }

    /**
     * 裁剪工具描述
     */
    public String trimToolsDescription(String toolsDesc, int maxChars) {
        if (toolsDesc == null) return "";
        if (toolsDesc.length() <= maxChars) return toolsDesc;
        // 粗暴截断，可改进为保留开头和结尾
        return toolsDesc.substring(0, maxChars - 10) + "...";
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}