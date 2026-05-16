package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.file.Files;
import java.nio.file.Path;

@ToolHandler(
        name = "delete_file",
        description = "删除沙箱内的文件或空目录（非递归，无法删除非空目录）。",
        category = ToolHandler.Category.FILE,
        location = ToolHandler.Location.SANDBOX,
        version = "1.0.0"
)
@SandboxPolicy(
        allowedPaths = {"documents", "projects", "downloads", "tmp"},
        maxExecutionSeconds = 5
)
public class DeleteFileHandler {

    @Autowired
    private SandboxManager sandboxManager;

    public String execute(
            @ToolParameter(value = "path", description = "要删除的文件或空目录路径", example = "tmp/old.txt")
            String path
    ) throws Exception {
        Path safePath = sandboxManager.resolve(path);
        if (!Files.exists(safePath)) {
            return "错误：文件/目录不存在 - " + safePath.toString();
        }
        if (Files.isDirectory(safePath) && Files.list(safePath).findAny().isPresent()) {
            return "错误：目录非空，无法删除 - " + safePath.toString();
        }
        Files.delete(safePath);
        return "删除成功: " + safePath.toString();
    }
}