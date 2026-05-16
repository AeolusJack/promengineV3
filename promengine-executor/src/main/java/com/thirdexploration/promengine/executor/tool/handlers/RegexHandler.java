package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ToolHandler(
        name = "regex",
        description = "正则表达式操作：匹配判断、查找所有、替换。",
        category = ToolHandler.Category.UTILITY,
        location = ToolHandler.Location.LOCAL,
        version = "1.0.0"
)
@SandboxPolicy(allowedPaths = {})
public class RegexHandler {

    public String execute(
            @ToolParameter(value = "operation", description = "操作: matches, find_all, replace")
            String operation,
            @ToolParameter(value = "regex", description = "正则表达式")
            String regex,
            @ToolParameter(value = "text", description = "待处理的文本")
            String text,
            @ToolParameter(value = "replacement", description = "替换文本（仅replace操作需要）", required = false)
            String replacement,
            @ToolParameter(value = "case_insensitive", description = "是否忽略大小写，默认false", required = false)
            Boolean caseInsensitive
    ) {
        if (regex == null || text == null) return "错误：缺少 regex 或 text 参数";
        int flags = (caseInsensitive != null && caseInsensitive) ? Pattern.CASE_INSENSITIVE : 0;
        Pattern pattern = Pattern.compile(regex, flags);
        Matcher matcher = pattern.matcher(text);
        switch (operation.toLowerCase()) {
            case "matches":
                return "匹配结果: " + matcher.matches();
            case "find_all":
                return "找到匹配项: " + matcher.results()
                        .map(m -> m.group())
                        .collect(Collectors.joining("\n"));
            case "replace":
                String repl = replacement != null ? replacement : "";
                return "替换结果:\n" + matcher.replaceAll(repl);
            default:
                return "不支持的操作: " + operation;
        }
    }
}