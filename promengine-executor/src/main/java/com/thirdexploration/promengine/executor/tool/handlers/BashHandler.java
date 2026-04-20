package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@ToolHandler(
        name = "execute_bash",
        description = "在沙箱工作区内执行 Bash 命令",
        category = ToolHandler.Category.COMMAND,
        location = ToolHandler.Location.SANDBOX,
        version = "1.2.0"
)
@SandboxPolicy(
        allowedPaths = {"documents", "projects", "downloads"},
        maxExecutionSeconds = 30
)
public class BashHandler {

    @Autowired
    private SandboxManager sandboxManager;

    public String execute(
            @ToolParameter(value = "command", description = "要执行的命令", example = "ls -la")
            String command,
            @ToolParameter(value = "working_dir", description = "工作目录", required = false, example = "projects")
            String workingDir) {

        Path workDir = sandboxManager.getWorkspaceRoot();
        if (workingDir != null && !workingDir.isEmpty()) {
            workDir = sandboxManager.resolve(workingDir);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "命令执行超时（30秒）";
            }
            return output.toString();
        } catch (Exception e) {
            return "命令执行失败：" + e.getMessage();
        }
    }
}