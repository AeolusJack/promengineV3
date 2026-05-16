package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;

import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ToolHandler(
        name = "text_process",
        description = "对文本进行常见处理：替换、正则提取、大小写转换、拆分、拼接等。",
        category = ToolHandler.Category.UTILITY,
        location = ToolHandler.Location.LOCAL,
        version = "1.0.0"
)
@SandboxPolicy(allowedPaths = {})
public class TextProcessHandler {

    public String execute(
            @ToolParameter(value = "text", description = "输入文本")
            String text,
            @ToolParameter(value = "operation", description = "操作类型: replace, regex_extract, to_upper, to_lower, trim, split, join_lines")
            String operation,
            @ToolParameter(value = "pattern", description = "用于 replace 或 regex_extract 的正则表达式", required = false)
            String pattern,
            @ToolParameter(value = "replacement", description = "替换的目标字符串", required = false)
            String replacement,
            @ToolParameter(value = "delimiter", description = "split 操作的分隔符", required = false)
            String delimiter,
            @ToolParameter(value = "join_char", description = "join_lines 的连接字符，默认换行", required = false)
            String joinChar
    ) {
        if (text == null) return "";
        switch (operation.toLowerCase()) {
            case "replace":
                if (pattern == null) return "错误：缺少 pattern";
                String repl = replacement != null ? replacement : "";
                return text.replaceAll(pattern, repl);
            case "regex_extract":
                if (pattern == null) return "错误：缺少 pattern";
                var matcher = Pattern.compile(pattern).matcher(text);
                if (matcher.find()) {
                    return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
                }
                return "";
            case "to_upper":
                return text.toUpperCase();
            case "to_lower":
                return text.toLowerCase();
            case "trim":
                return text.trim();
            case "split":
                if (delimiter == null) delimiter = ",";
                String[] parts = text.split(Pattern.quote(delimiter));
                return String.join("\n", parts);
            case "join_lines":
                String joiner = joinChar != null ? joinChar : "\n";
                return Stream.of(text.split("\n")).collect(Collectors.joining(joiner));
            default:
                return "不支持的操作: " + operation;
        }
    }
}