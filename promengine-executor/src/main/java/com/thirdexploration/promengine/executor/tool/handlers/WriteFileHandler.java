package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import com.thirdexploration.promengine.executor.tool.registry.ToolAutoRegistrar;
import com.thirdexploration.promengine.executor.tool.registry.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@ToolHandler(
        name = "write_file",
        description = "在授权工作区内创建或覆盖文件",
        category = ToolHandler.Category.FILE,
        location = ToolHandler.Location.SANDBOX,
        version = "1.2.0"
)
@SandboxPolicy(
        allowedPaths = {"documents", "projects", "downloads"},
        maxMemoryMB = 64,
        maxExecutionSeconds = 10
)
public class WriteFileHandler {

    @Autowired
    private SandboxManager sandboxManager;

    // 删除 @Autowired private ToolAutoRegistrar toolAutoRegistrar;

    public String execute(
            @ToolParameter(value = "path", description = "文件相对路径", example = "notes/meeting.txt")
            String path,
            @ToolParameter(value = "content", description = "要写入的内容", example = "会议纪要...")
            String content,
            @ToolParameter(value = "append", description = "是否追加模式", required = false)
            Boolean append) throws IOException {

        // 静态调用获取策略定义
        SandboxPolicy policyAnno = this.getClass().getAnnotation(SandboxPolicy.class);
        ToolDefinition.SandboxPolicyDef policyDef = ToolAutoRegistrar.buildSandboxPolicyDef(policyAnno);
        Path safePath = sandboxManager.resolve(path, policyDef);

        Files.createDirectories(safePath.getParent());
        if (Boolean.TRUE.equals(append)) {
            Files.writeString(safePath, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            Files.writeString(safePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        return "文件写入成功：" + safePath;
    }
}