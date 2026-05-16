package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ToolHandler(
        name = "search_files",
        description = "在沙箱目录中按文件名模式或文件内容搜索文件。支持通配符和正则表达式。",
        category = ToolHandler.Category.FILE,
        location = ToolHandler.Location.SANDBOX,
        version = "1.0.0"
)
@SandboxPolicy(allowedPaths = {"documents", "projects", "downloads", "tmp"})
public class SearchFilesHandler {

    @Autowired
    private SandboxManager sandboxManager;

    private static final int MAX_MATCH_LINES = 50;  // 内容匹配最大行数
    private static final int MAX_RESULTS = 100;     // 最大文件结果数

    public String execute(
            @ToolParameter(value = "path", description = "搜索起始目录（相对于沙箱根）", example = "projects")
            String path,
            @ToolParameter(value = "name_pattern", description = "文件名模式，支持通配符如 *.java 或正则 regex:pattern", required = false)
            String namePattern,
            @ToolParameter(value = "content_pattern", description = "在文件内容中搜索的正则表达式", required = false)
            String contentPattern,
            @ToolParameter(value = "case_sensitive", description = "是否区分大小写，默认false", required = false)
            Boolean caseSensitive
    ) throws IOException {
        boolean caseInsensitive = (caseSensitive == null || !caseSensitive);
        Path startDir = sandboxManager.resolve(path);
        if (!Files.isDirectory(startDir)) {
            return "错误：路径不是目录 - " + startDir;
        }

        // 编译文件名匹配器
        PathMatcher nameMatcher = null;
        if (namePattern != null && !namePattern.isBlank()) {
            if (namePattern.startsWith("regex:")) {
                String regex = namePattern.substring(6);
                Pattern pattern = caseInsensitive ? Pattern.compile(regex, Pattern.CASE_INSENSITIVE) : Pattern.compile(regex);
                nameMatcher = p -> pattern.matcher(p.getFileName().toString()).matches();
            } else {
                String glob = caseInsensitive ? "glob:" + namePattern.toLowerCase() : "glob:" + namePattern;
                nameMatcher = FileSystems.getDefault().getPathMatcher(glob);
            }
        }

        // 编译内容匹配器
        Pattern contentRegex = null;
        if (contentPattern != null && !contentPattern.isBlank()) {
            int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
            contentRegex = Pattern.compile(contentPattern, flags);
        }

        List<SearchResult> results = new ArrayList<>();
        PathMatcher finalNameMatcher = nameMatcher;
        Pattern finalContentRegex = contentRegex;
        Files.walkFileTree(startDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (results.size() >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                // 文件名匹配
                boolean nameMatch = (finalNameMatcher == null) || finalNameMatcher.matches(file);
                if (!nameMatch) return FileVisitResult.CONTINUE;

                // 内容匹配
                boolean contentMatch = true;
                String preview = "";
                if (finalContentRegex != null) {
                    try {
                        List<String> lines = Files.readAllLines(file);
                        List<String> matchedLines = new ArrayList<>();
                        for (String line : lines) {
                            if (finalContentRegex.matcher(line).find()) {
                                matchedLines.add(line.trim());
                                if (matchedLines.size() >= MAX_MATCH_LINES) break;
                            }
                        }
                        contentMatch = !matchedLines.isEmpty();
                        if (contentMatch) {
                            preview = matchedLines.stream().limit(3).collect(Collectors.joining("\n  "));
                            if (matchedLines.size() > 3) preview += "\n  ...";
                        }
                    } catch (IOException e) {
                        contentMatch = false;
                    }
                }
                if (contentMatch) {
                    long size = attrs.size();
                    results.add(new SearchResult(startDir.relativize(file).toString(), size, preview));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (results.isEmpty()) {
            return "未找到匹配的文件。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(results.size()).append(" 个文件：\n");
        for (SearchResult r : results) {
            sb.append(r.path).append(" (").append(r.size).append(" 字节)");
            if (!r.preview.isEmpty()) sb.append("\n  内容预览: ").append(r.preview);
            sb.append("\n");
        }
        return sb.toString();
    }

    private static class SearchResult {
        String path;
        long size;
        String preview;
        SearchResult(String path, long size, String preview) {
            this.path = path; this.size = size; this.preview = preview;
        }
    }
}