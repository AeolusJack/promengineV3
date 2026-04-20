package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;

@ToolHandler(
        name = "read_file",
        description = "读取沙箱工作区内的文件内容",
        category = ToolHandler.Category.FILE,
        location = ToolHandler.Location.SANDBOX,
        version = "1.2.0"
)
@SandboxPolicy(allowedPaths = {"documents", "projects", "downloads"})
public class ReadFileHandler {

    @Autowired
    private SandboxManager sandboxManager;

    public String execute(
            @ToolParameter(value = "path", description = "文件路径", example = "notes/meeting.txt")
            String path) throws Exception {

        Path safePath = sandboxManager.resolve(path);
        if (!Files.exists(safePath)) {
            return "错误：文件不存在";
        }
        return Files.readString(safePath);
    }
}