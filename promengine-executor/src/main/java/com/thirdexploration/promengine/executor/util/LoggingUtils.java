package com.thirdexploration.promengine.executor.util;

import com.thirdexploration.promengine.core.MemoryService;
import com.thirdexploration.promengine.core.domain.SearchResult;
import com.thirdexploration.promengine.memory.retrieval.RetrievalEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.List;



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
     * 专门打印 Prompt 组装详情，高亮向量检索部分。
     *
     * @param systemPrompt      系统提示词
     * @param memories          向量检索到的记忆片段列表
     * @param conversation      当前对话上下文
     * @param finalPrompt       最终拼接后的完整 Prompt
     */
    public static void debugPromptAssembly(String systemPrompt,
                                           List<String> memories,
                                           String conversation,
                                           String finalPrompt) {
        if (!log.isDebugEnabled()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\n========== PROMPT 组装详情 (DEBUG) ==========\n");

        // 系统提示词
        sb.append("[系统指令] (").append(systemPrompt.length()).append(" chars):\n");
        sb.append(truncateWithEllipsis(systemPrompt, MAX_PREVIEW_LENGTH)).append("\n\n");

        // 向量检索到的记忆（高亮）
        sb.append("[向量检索注入] 共 ").append(memories.size()).append(" 条记忆:\n");
        for (int i = 0; i < memories.size(); i++) {
            sb.append("  [").append(i + 1).append("] ");
            sb.append(truncateWithEllipsis(memories.get(i), MAX_PREVIEW_LENGTH)).append("\n");
        }
        sb.append("\n");

        // 对话历史
        sb.append("[对话上下文] (").append(conversation.length()).append(" chars):\n");
        sb.append(truncateWithEllipsis(conversation, MAX_PREVIEW_LENGTH)).append("\n\n");

        // 最终完整 Prompt 预览
        sb.append("[最终 Prompt] 总长度 ").append(finalPrompt.length()).append(" chars:\n");
        sb.append(truncateWithEllipsis(finalPrompt, MAX_PREVIEW_LENGTH));
        sb.append("\n==============================================");

        log.debug(sb.toString());
    }
    public static void debugMemoryFusion(MemoryService.RetrievalDetails details, String finalPrompt) {
        if (!log.isDebugEnabled()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║              记忆融合详情 (Memory Fusion Details)               ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════╣\n");

        appendSourceDetails(sb, "🔥 热存储 (Hot)", details.getHotHits());
        appendSourceDetails(sb, "📦 温存储摘要 (Warm Summary)", details.getWarmSummaryHits());
        appendSourceDetails(sb, "🔍 Lucene 关键词", details.getLuceneHits());
        appendSourceDetails(sb, "🧠 向量语义 (Vector)", details.getVectorHits());
        appendSourceDetails(sb, "✨ RRF 融合后 (Fused)", details.getFusedHits());

        sb.append("╠════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ 最终 Prompt预览，已 根据配置，对记忆进行截断压缩，后续向量存储只存入摘要总结║\n");
        sb.append("╠════════════════════════════════════════════════════════════════╣\n");
        sb.append(truncateWithEllipsis(finalPrompt, 300)).append("\n");
        sb.append("╚════════════════════════════════════════════════════════════════╝");

        log.debug(sb.toString());
    }

    private static void appendSourceDetails(StringBuilder sb, String title,List<SearchResult.MemoryHit> hits) {
        sb.append(String.format("║ %-62s ║\n", title));
        sb.append("║   命中数: ").append(hits != null ? hits.size() : 0).append("\n");
        if (hits != null && !hits.isEmpty()) {
            for (int i = 0; i < Math.min(3, hits.size()); i++) {
                SearchResult.MemoryHit hit = hits.get(i);
                String preview = hit.getContent().replace("\n", " ").substring(0, Math.min(40, hit.getContent().length()));
                sb.append(String.format("║     [%d] score=%.2f  %s...\n", i+1, hit.getScore(), preview));
            }
            if (hits.size() > 3) sb.append("║     ... 等 ").append(hits.size()).append(" 条\n");
        }
        sb.append("║\n");
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