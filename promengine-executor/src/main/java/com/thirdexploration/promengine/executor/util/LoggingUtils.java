package com.thirdexploration.promengine.executor.util;

import com.thirdexploration.promengine.memory.model.MemoryEntry;
import com.thirdexploration.promengine.memory.model.MemoryQuery;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public final class LoggingUtils {

    private static final int MAX_PREVIEW_LENGTH = 300;
    private static final String OMISSION = "\n... [内容过长，中间部分已省略] ...\n";

    private LoggingUtils() {}

    /**
     * 优雅打印长文本：如果长度超过 600，则只打印前后各 300 字符。
     */
    public static void debugLongText(String prefix, String fullText) {
        if (!log.isDebugEnabled()) return;

        String preview = truncateWithEllipsis(fullText, MAX_PREVIEW_LENGTH * 2);
        log.debug("{}:\n{}", prefix, preview);
    }

    /**
     * 打印 Prompt 组装详情（新版，基于 MemoryEntry）。
     *
     * @param systemPrompt   系统提示词
     * @param memories       检索到的记忆列表
     * @param conversation   对话上下文
     * @param finalPrompt    最终拼接的 Prompt
     */
    public static void debugPromptAssembly(String systemPrompt,
                                           List<MemoryEntry> memories,
                                           String conversation,
                                           String finalPrompt) {
        if (!log.isDebugEnabled()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\n========== PROMPT 组装详情 (Aeon) ==========\n");

        // 系统提示词
        sb.append("[系统指令] (").append(systemPrompt.length()).append(" chars):\n");
        sb.append(truncateWithEllipsis(systemPrompt, MAX_PREVIEW_LENGTH)).append("\n\n");

        // 检索到的记忆（按层级分组）
        sb.append("[记忆检索结果] 共 ").append(memories.size()).append(" 条:\n");
        Map<String, List<MemoryEntry>> byLayer = memories.stream()
                .collect(Collectors.groupingBy(m -> m.getLayer() != null ? m.getLayer() : "unknown"));
        byLayer.forEach((layer, layerMemories) -> {
            sb.append("  【").append(layer).append("层】").append(layerMemories.size()).append("条:\n");
            for (int i = 0; i < Math.min(3, layerMemories.size()); i++) {
                MemoryEntry mem = layerMemories.get(i);
                String preview = truncateWithEllipsis(
                        mem.getSummary() != null ? mem.getSummary() : mem.getContent(), 80);
                sb.append(String.format("    [%d] domain=%s, strength=%.2f  %s\n",
                        i + 1, mem.getDomain(), mem.getStrength(), preview));
            }
            if (layerMemories.size() > 3) {
                sb.append("    ... 等 ").append(layerMemories.size()).append(" 条\n");
            }
        });
        sb.append("\n");

        // 对话历史
        sb.append("[对话上下文] (").append(conversation.length()).append(" chars):\n");
        sb.append(truncateWithEllipsis(conversation, MAX_PREVIEW_LENGTH)).append("\n\n");

        // 最终 Prompt
        sb.append("[最终 Prompt] 总长度 ").append(finalPrompt.length()).append(" chars:\n");
        sb.append(truncateWithEllipsis(finalPrompt, MAX_PREVIEW_LENGTH));
        sb.append("\n==============================================");

        log.debug(sb.toString());
    }

    /**
     * 打印记忆检索详情（新版，用于调试检索过程）。
     *
     * @param query   查询条件
     * @param results 检索结果
     */
    public static void debugMemoryRecall(MemoryQuery query, List<MemoryEntry> results) {
        if (!log.isDebugEnabled()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                   Aeon 记忆检索详情                           ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ 查询文本: %s\n", truncateWithEllipsis(query.getText(), 50)));
        sb.append(String.format("║ 用户: %s, 会话: %s\n", query.getUserId(), query.getSessionId()));
        sb.append(String.format("║ 域: %s, 层级: %s\n",
                query.getDomains() != null ? query.getDomains() : query.getDomain(),
                query.isIncludeEpisodic() ? "episodic+semantic" : "semantic"));
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ 返回结果数: %d\n", results.size()));

        Map<String, List<MemoryEntry>> byDomain = results.stream()
                .collect(Collectors.groupingBy(m -> m.getDomain() != null ? m.getDomain() : "general"));
        byDomain.forEach((domain, domainMemories) -> {
            sb.append(String.format("║  域 [%s] %d条:\n", domain, domainMemories.size()));
            for (int i = 0; i < Math.min(2, domainMemories.size()); i++) {
                MemoryEntry mem = domainMemories.get(i);
                String preview = mem.getSummary() != null ? mem.getSummary() : mem.getContent();
                preview = preview.replace("\n", " ").substring(0, Math.min(60, preview.length()));
                sb.append(String.format("║    - %s...\n", preview));
            }
        });
        sb.append("╚══════════════════════════════════════════════════════════════╝");
        log.debug(sb.toString());
    }



    /**
     * 截断文本：如果长度 <= maxLength*2 则原样返回；
     * 否则返回前 maxLength 字符 + 省略提示 + 后 maxLength 字符。
     */
    private static String truncateWithEllipsis(String text, int maxLength) {
        if (text == null) return "null";
        int threshold = maxLength * 2;
        if (text.length() <= threshold) {
            return text;
        }
        String head = text.substring(0, maxLength);
        String tail = text.substring(text.length() - maxLength);
        return head + OMISSION + tail;
    }
}