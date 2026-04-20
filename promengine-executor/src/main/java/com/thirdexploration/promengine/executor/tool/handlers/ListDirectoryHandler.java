package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

@ToolHandler(
        name = "list_directory",
        description = "列出沙箱工作区内的目录内容",
        category = ToolHandler.Category.FILE,
        location = ToolHandler.Location.SANDBOX,
        version = "1.2.0"
)
public class ListDirectoryHandler {

    @Autowired
    private SandboxManager sandboxManager;

    public String execute(
            @ToolParameter(value = "path", description = "目录路径", example = "documents")
            String path) {

        Path safePath = sandboxManager.resolve(path);
        if (!Files.exists(safePath)) {
            return "错误：目录不存在";
        }
        if (!Files.isDirectory(safePath)) {
            return "错误：路径不是目录";
        }
        try (var stream = Files.list(safePath)) {
            return stream.map(p -> p.getFileName().toString())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "读取目录失败：" + e.getMessage();
        }
    }
}