package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

@ToolHandler(
        name = "file_exists",
        description = "检查沙箱内文件或目录是否存在，并返回类型、大小、修改时间。",
        category = ToolHandler.Category.FILE,
        location = ToolHandler.Location.SANDBOX,
        version = "1.0.0"
)
@SandboxPolicy(
        allowedPaths = {"documents", "projects", "downloads", "tmp"},
        maxExecutionSeconds = 5
)
public class FileExistsHandler {

    @Autowired
    private SandboxManager sandboxManager;

    public String execute(
            @ToolParameter(value = "path", description = "文件或目录路径", example = "documents/report.pdf")
            String path
    ) throws Exception {
        Path safePath = sandboxManager.resolve(path);
        if (!Files.exists(safePath)) {
            return "不存在: " + safePath.toString();
        }
        boolean isDir = Files.isDirectory(safePath);
        long size = isDir ? 0 : Files.size(safePath);
        BasicFileAttributes attrs = Files.readAttributes(safePath, BasicFileAttributes.class);
        return String.format("存在\n类型: %s\n大小: %d 字节\n修改时间: %s",
                isDir ? "目录" : "文件",
                size,
                attrs.lastModifiedTime());
    }
}