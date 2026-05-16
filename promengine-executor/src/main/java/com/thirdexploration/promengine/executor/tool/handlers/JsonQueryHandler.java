package com.thirdexploration.promengine.executor.tool.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;

@ToolHandler(
        name = "json_query",
        description = "对 JSON 字符串进行查询，支持点号路径和数组索引（例如 'data.results[0].name'）。注意：输入的 JSON 必须是完整有效的格式，不能包含 '...' 占位符。",
        category = ToolHandler.Category.UTILITY,
        location = ToolHandler.Location.LOCAL,
        version = "1.1.0"
)
@SandboxPolicy(allowedPaths = {})
public class JsonQueryHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    public String execute(
            @ToolParameter(value = "json", description = "JSON 字符串（必须完整有效）")
            String json,
            @ToolParameter(value = "path", description = "查询路径，支持点号和数组索引，如 'data.items[0].name'")
            String path
    ) throws Exception {
        if (json == null || json.isBlank()) {
            return "错误：json 参数为空";
        }
        if (path == null || path.isBlank()) {
            return "错误：path 参数为空";
        }
        // 预处理：移除 JSON 中的非法占位符（如 ... 或 [...]）
        String cleanJson = preprocessJson(json);
        JsonNode root;
        try {
            root = mapper.readTree(cleanJson);
        } catch (Exception e) {
            return "JSON 解析失败: " + e.getMessage() + "\n请确保 JSON 完整且不包含 '...' 等占位符。";
        }
        JsonNode result = query(root, path);
        if (result == null || result.isMissingNode()) {
            return "未找到匹配的值";
        }
        return mapper.writeValueAsString(result);
    }

    /**
     * 预处理 JSON 字符串，移除非法占位符：
     * - 将 "..." 替换为空字符串（出现在值位置）
     * - 将 "[...]" 替换为 "[]"（空数组）
     */
    private String preprocessJson(String json) {
        // 移除字符串值中的 ... (仅处理不在双引号内的，简单全文本替换)
        // 更严谨的做法是使用正则确保不破坏字符串内容，但为了简单，我们直接替换
        String cleaned = json.replaceAll("\"\\s*\\.\\.\\.\\s*\"", "\"\"");
        cleaned = cleaned.replaceAll("\\[\\s*\\.\\.\\.\\s*\\]", "[]");
        // 处理像 hourly:[...] 的情况（上面已经处理）
        return cleaned;
    }

    private JsonNode query(JsonNode node, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = node;
        for (String part : parts) {
            if (current == null) return null;
            // 处理数组索引如 items[0]
            if (part.contains("[") && part.contains("]")) {
                int idx = part.indexOf('[');
                String arrayName = part.substring(0, idx);
                String indexStr = part.substring(idx + 1, part.length() - 1);
                int index;
                try {
                    index = Integer.parseInt(indexStr);
                } catch (NumberFormatException e) {
                    return null;
                }
                current = current.get(arrayName);
                if (current != null && current.isArray() && index >= 0 && index < current.size()) {
                    current = current.get(index);
                } else {
                    return null;
                }
            } else {
                current = current.get(part);
            }
        }
        return current;
    }
}