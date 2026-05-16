package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ToolHandler(
        name = "execute_python",
        description = "执行 Python 脚本文件或代码片段。支持传递参数、预安装依赖包。自动适配 Windows/Linux/macOS 的 python 命令。",
        category = ToolHandler.Category.CODE,
        location = ToolHandler.Location.SANDBOX,
        version = "1.1.0"
)
@SandboxPolicy(
        allowedPaths = {"documents", "projects", "downloads", "tmp"},
        maxExecutionSeconds = 60
)
public class PythonExecutorHandler {

    @Autowired
    private SandboxManager sandboxManager;

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private final String pythonCommand;

    public PythonExecutorHandler() {
        // 自动检测操作系统
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            pythonCommand = "python";
        } else {
            pythonCommand = "python3";
        }
    }

    public String execute(
            @ToolParameter(value = "script_path", description = "沙箱内的 Python 脚本路径（如 projects/main.py）。优先使用此参数执行完整文件。", required = false)
            String scriptPath,
            @ToolParameter(value = "code", description = "Python 代码字符串（当 script_path 未提供时执行）", required = false)
            String code,
            @ToolParameter(value = "args", description = "传递给脚本的命令行参数（JSON 数组字符串，如 [\"arg1\",\"arg2\"]）", required = false)
            String argsJson,
            @ToolParameter(value = "install_packages", description = "需要预安装的 pip 包列表，JSON 数组字符串，如 [\"requests\",\"numpy\"]", required = false)
            String installPackages,
            @ToolParameter(value = "timeout_seconds", description = "执行超时秒数，默认30", required = false)
            Integer timeoutSeconds
    ) throws Exception {
        int timeout = (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;

        // 1. 处理依赖安装
        if (installPackages != null && !installPackages.isBlank()) {
            String installResult = installPythonPackages(installPackages);
            if (!installResult.startsWith("成功")) {
                return installResult;
            }
        }

        // 2. 确定要执行的脚本路径和命令
        Path scriptFile = null;
        boolean isTempFile = false;

        if (scriptPath != null && !scriptPath.isBlank()) {
            // 执行现有脚本文件
            scriptFile = sandboxManager.resolve(scriptPath);
            if (!Files.exists(scriptFile)) {
                return "错误：脚本文件不存在 - " + scriptFile;
            }
            if (!Files.isRegularFile(scriptFile) || !scriptFile.toString().endsWith(".py")) {
                return "错误：路径不是 Python 文件 - " + scriptFile;
            }
        } else if (code != null && !code.isBlank()) {
            // 临时创建代码文件
            String fileName = "tmp_py_" + UUID.randomUUID().toString().replace("-", "") + ".py";
            scriptFile = sandboxManager.resolve("tmp/" + fileName);
            Files.createDirectories(scriptFile.getParent());
            Files.writeString(scriptFile, code);
            isTempFile = true;
        } else {
            return "错误：必须提供 script_path 或 code 参数";
        }

        // 3. 构建命令
        String[] args = parseArgs(argsJson);
        String[] cmd = new String[args.length + 2];
        cmd[0] = pythonCommand;
        cmd[1] = scriptFile.toString();
        System.arraycopy(args, 0, cmd, 2, args.length);

        // 4. 执行进程
        Path workingDir = sandboxManager.getWorkspaceRoot();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                output.append(buffer, 0, read);
                // 防止输出过大（可选限制）
                if (output.length() > 10 * 1024 * 1024) { // 10MB 上限
                    output.append("\n... (输出过长，已截断)");
                    break;
                }
            }
        }

        boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
        int exitCode = process.exitValue();

        // 清理临时文件
        if (isTempFile && scriptFile != null && Files.exists(scriptFile)) {
            Files.deleteIfExists(scriptFile);
        }

        if (!finished) {
            process.destroyForcibly();
            return "Python 执行超时（" + timeout + " 秒）\n已输出内容:\n" + output;
        }

        String result = output.toString();
        if (exitCode != 0) {
            return "Python 进程异常退出 (exit code " + exitCode + ")\n输出:\n" + result;
        }
        return result.isEmpty() ? "执行成功（无输出）" : result;
    }

    private String installPythonPackages(String packagesJson) {
        try {
            String[] packages = parseJsonArray(packagesJson);
            if (packages.length == 0) return "无需安装";
            StringBuilder cmd = new StringBuilder();
            if (pythonCommand.equals("python")) {
                cmd.append("python -m pip install ");
            } else {
                cmd.append("python3 -m pip install ");
            }
            for (String pkg : packages) {
                cmd.append(pkg).append(" ");
            }
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", cmd.toString());
            if (!pythonCommand.equals("python")) {
                pb = new ProcessBuilder("bash", "-c", cmd.toString());
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    // 忽略安装细节，可改为记录日志
                }
            }
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                return "安装依赖失败，请检查包名和网络";
            }
            return "成功安装 " + packages.length + " 个包";
        } catch (Exception e) {
            return "安装依赖异常: " + e.getMessage();
        }
    }

    private String[] parseJsonArray(String json) {
        if (json == null || json.isBlank()) return new String[0];
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) return new String[0];
        json = json.substring(1, json.length() - 1);
        if (json.isBlank()) return new String[0];
        String[] parts = json.split(",");
        String[] result = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = parts[i].trim().replaceAll("^\"|\"$", "");
        }
        return result;
    }

    private String[] parseArgs(String argsJson) {
        return parseJsonArray(argsJson);
    }
}