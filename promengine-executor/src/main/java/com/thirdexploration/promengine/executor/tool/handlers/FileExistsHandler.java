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
import java.util.stream.Stream;

@ToolHandler(
        name = "file_exists",
        description = "检查文件或目录是否存在。支持两种模式：1) 精确路径（包含路径分隔符） 2) 智能文件名搜索（仅提供文件名时，在沙箱所有允许目录中递归查找，返回所有匹配项）。",
        category = ToolHandler.Category.FILE,
        location = ToolHandler.Location.SANDBOX,
        version = "2.0.0"
)
@SandboxPolicy(
        allowedPaths = {"documents", "projects", "downloads", "tmp"},
        maxExecutionSeconds = 30   // 搜索可能需要更多时间
)
public class FileExistsHandler {

    @Autowired
    private SandboxManager sandboxManager;

    // 最大搜索结果数，避免返回过多
    private static final int MAX_RESULTS = 20;
    // 最大递归深度
    private static final int MAX_DEPTH = 10;

    public String execute(
            @ToolParameter(value = "path", description = "文件或目录路径，可以包含路径分隔符（精确模式），也可以只是一个文件名（模糊搜索模式）", example = "documents/report.pdf 或 myfile.txt")
            String path
    ) throws Exception {
        if (path == null || path.isBlank()) {
            return "错误：未提供路径";
        }

        // 自动识别模式：如果包含路径分隔符（/ 或 \），或包含点号且可能为扩展名（非简单文件名），可精确模式
        // 更可靠：检查是否包含分隔符或是否为绝对路径
        boolean isExactPath = path.contains("/") || path.contains("\\") || path.startsWith(".") || path.contains(":");
        if (isExactPath) {
            // 精确路径模式
            return checkExactPath(path);
        } else {
            // 智能文件名搜索模式
            return searchByFileName(path);
        }
    }

    /**
     * 精确路径检查（原有逻辑）
     */
    private String checkExactPath(String path) throws IOException {
        Path safePath = sandboxManager.resolve(path);
        if (!Files.exists(safePath)) {
            return "不存在: " + safePath.toString();
        }
        boolean isDir = Files.isDirectory(safePath);
        long size = isDir ? 0 : Files.size(safePath);
        BasicFileAttributes attrs = Files.readAttributes(safePath, BasicFileAttributes.class);
        return String.format("存在\n类型: %s\n路径: %s\n大小: %d 字节\n修改时间: %s",
                isDir ? "目录" : "文件",
                safePath.toString(),
                size,
                attrs.lastModifiedTime());
    }

    /**
     * 根据文件名在沙箱允许的目录中递归搜索
     */
    private String searchByFileName(String fileName) throws IOException {
        // 获取沙箱根目录
        Path root = sandboxManager.getWorkspaceRoot();
        // 允许的搜索起始目录（相对于沙箱根）
        List<String> allowedSubDirs = List.of("documents", "projects", "downloads", "tmp");
        List<FileMatch> matches = new ArrayList<>();

        for (String sub : allowedSubDirs) {
            Path startDir = root.resolve(sub);
            if (!Files.isDirectory(startDir)) continue;
            // 递归遍历，限制深度
            Files.walkFileTree(startDir, new SimpleFileVisitor<>() {
                private int depth = 0;
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    depth++;
                    if (depth > MAX_DEPTH) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    depth--;
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (matches.size() >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                    if (file.getFileName().toString().equals(fileName)) {
                        try {
                            matches.add(new FileMatch(
                                    root.relativize(file).toString(),
                                    Files.size(file),
                                    attrs.lastModifiedTime()
                            ));
                        } catch (IOException ignored) {}
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (matches.size() >= MAX_RESULTS) break;
        }

        if (matches.isEmpty()) {
            return "未找到名为 '" + fileName + "' 的文件。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(matches.size()).append(" 个匹配文件：\n");
        for (int i = 0; i < matches.size(); i++) {
            FileMatch m = matches.get(i);
            sb.append(i + 1).append(". ")
                    .append("路径: ").append(m.relativePath)
                    .append(", 大小: ").append(m.size).append(" 字节")
                    .append(", 修改时间: ").append(m.lastModified)
                    .append("\n");
        }
        if (matches.size() == MAX_RESULTS) {
            sb.append("(仅显示前 ").append(MAX_RESULTS).append(" 个结果，如需更多请缩小搜索范围)");
        }
        return sb.toString();
    }

    private static class FileMatch {
        String relativePath;
        long size;
        Object lastModified;
        FileMatch(String path, long size, Object lastModified) {
            this.relativePath = path;
            this.size = size;
            this.lastModified = lastModified;
        }
    }
}