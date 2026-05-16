package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import java.util.stream.Stream;

@ToolHandler(
        name = "archive",
        description = "压缩或解压文件/目录。支持 ZIP 格式。",
        category = ToolHandler.Category.FILE,
        location = ToolHandler.Location.SANDBOX,
        version = "1.0.0"
)
@SandboxPolicy(allowedPaths = {"documents", "projects", "downloads", "tmp"})
public class ArchiveHandler {

    @Autowired
    private SandboxManager sandboxManager;

    public String execute(
            @ToolParameter(value = "operation", description = "操作类型: zip (压缩) 或 unzip (解压)")
            String operation,
            @ToolParameter(value = "source", description = "要压缩的源路径（文件或目录）或要解压的 ZIP 文件路径")
            String source,
            @ToolParameter(value = "destination", description = "目标路径：压缩时生成的 ZIP 文件路径，解压时的目标目录")
            String destination
    ) throws IOException {
        Path src = sandboxManager.resolve(source);
        Path dest = sandboxManager.resolve(destination);

        if (!Files.exists(src)) {
            return "错误：源路径不存在 - " + src;
        }

        if ("zip".equalsIgnoreCase(operation)) {
            return zip(src, dest);
        } else if ("unzip".equalsIgnoreCase(operation)) {
            return unzip(src, dest);
        } else {
            return "不支持的操作: " + operation;
        }
    }

    private String zip(Path source, Path zipFile) throws IOException {
        if (Files.exists(zipFile)) {
            return "错误：目标 ZIP 文件已存在 - " + zipFile;
        }
        Files.createDirectories(zipFile.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walk(source).filter(p -> !Files.isDirectory(p)).forEach(p -> {
                String relativePath = source.relativize(p).toString().replace("\\", "/");
                try {
                    zos.putNextEntry(new ZipEntry(relativePath));
                    Files.copy(p, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return "压缩成功: " + zipFile + " (源: " + source + ")";
    }

    private String unzip(Path zipFile, Path destDir) throws IOException {
        if (!Files.isRegularFile(zipFile) || !zipFile.toString().endsWith(".zip")) {
            return "错误：源不是有效的 ZIP 文件 - " + zipFile;
        }
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = destDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destDir)) {
                    return "错误：ZIP 条目路径越界 - " + entry.getName();
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target);
                }
                zis.closeEntry();
            }
        }
        return "解压成功: " + zipFile + " -> " + destDir;
    }
}