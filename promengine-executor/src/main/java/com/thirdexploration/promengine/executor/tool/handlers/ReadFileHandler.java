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
        description = "读取沙箱工作区内的文件内容。如果只提供文件名，将尝试从 documents 目录读取。",
        category = ToolHandler.Category.FILE,
        location = ToolHandler.Location.SANDBOX,
        version = "1.2.0"
)
@SandboxPolicy(allowedPaths = {"documents", "projects", "downloads"})
public class ReadFileHandler {

    @Autowired
    private SandboxManager sandboxManager;

    public String execute(
            @ToolParameter(value = "path", description = "文件路径，例如 notes/meeting.txt 或直接文件名", example = "notes/meeting.txt")
            String path) throws Exception {

        // 如果路径是简单文件名，先尝试直接读取，若不存在则尝试 documents/ 前缀
        String effectivePath = path;
        if (!path.contains("/") && !path.contains("\\")) {
            Path direct = sandboxManager.resolve(path);
            if (!Files.exists(direct)) {
                effectivePath = "documents/" + path;
            }
        }
        Path safePath = sandboxManager.resolve(effectivePath);
        if (!Files.exists(safePath)) {
            return "错误：文件不存在 (" + effectivePath + ")";
        }
        return Files.readString(safePath);
    }
}