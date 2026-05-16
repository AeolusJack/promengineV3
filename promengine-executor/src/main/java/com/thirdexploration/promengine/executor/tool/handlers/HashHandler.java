package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

@ToolHandler(
        name = "hash",
        description = "计算文件或文本的哈希值（MD5, SHA-1, SHA-256）。",
        category = ToolHandler.Category.UTILITY,
        location = ToolHandler.Location.SANDBOX,
        version = "1.0.0"
)
@SandboxPolicy(allowedPaths = {"documents", "projects", "downloads", "tmp"})
public class HashHandler {

    @Autowired
    private SandboxManager sandboxManager;

    public String execute(
            @ToolParameter(value = "algorithm", description = "哈希算法: md5, sha1, sha256", required = false, example = "sha256")
            String algorithm,
            @ToolParameter(value = "path", description = "文件路径（相对于沙箱）", required = false)
            String path,
            @ToolParameter(value = "text", description = "文本内容", required = false)
            String text
    ) throws Exception {
        String algo = (algorithm == null || algorithm.isBlank()) ? "sha256" : algorithm.toLowerCase();
        MessageDigest md;
        switch (algo) {
            case "md5": md = MessageDigest.getInstance("MD5"); break;
            case "sha1": md = MessageDigest.getInstance("SHA-1"); break;
            case "sha256": md = MessageDigest.getInstance("SHA-256"); break;
            default: return "不支持的算法: " + algorithm;
        }

        if (path != null && !path.isBlank()) {
            Path filePath = sandboxManager.resolve(path);
            if (!Files.isRegularFile(filePath)) {
                return "错误：文件不存在或不是普通文件 - " + filePath;
            }
            try (InputStream is = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            byte[] hash = md.digest();
            return HexFormat.of().formatHex(hash).toLowerCase();
        } else if (text != null && !text.isBlank()) {
            byte[] hash = md.digest(text.getBytes());
            return HexFormat.of().formatHex(hash).toLowerCase();
        } else {
            return "错误：必须提供 path 或 text 参数";
        }
    }
}