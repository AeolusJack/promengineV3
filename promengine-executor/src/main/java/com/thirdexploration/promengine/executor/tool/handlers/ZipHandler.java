package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@ToolHandler(
        name = "zip",
        description = "ZIP 压缩/解压。压缩时支持文件或目录，解压到指定目录。",
        category = ToolHandler.Category.FILE,
        location = ToolHandler.Location.SANDBOX,
        version = "1.0.0"
)
@SandboxPolicy(allowedPaths = {"documents", "projects", "downloads", "tmp"})
public class ZipHandler {

    @Autowired
    private SandboxManager sandboxManager;

    public String execute(
            @ToolParameter(value = "operation", description = "操作: compress 或 decompress")
            String operation,
            @ToolParameter(value = "source", description = "压缩时: 要压缩的文件/目录路径；解压时: ZIP 文件路径")
            String source,
            @ToolParameter(value = "destination", description = "压缩时: 输出 ZIP 文件路径；解压时: 解压目标目录")
            String destination
    ) throws IOException {
        Path src = sandboxManager.resolve(source);
        Path dest = sandboxManager.resolve(destination);
        if ("compress".equalsIgnoreCase(operation)) {
            return compress(src, dest);
        } else if ("decompress".equalsIgnoreCase(operation)) {
            return decompress(src, dest);
        } else {
            return "不支持的操作: " + operation;
        }
    }

    private String compress(Path src, Path zipFile) throws IOException {
        if (!Files.exists(src)) return "错误：源文件/目录不存在 - " + src;
        Files.createDirectories(zipFile.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walkFileTree(src, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = src.relativize(file).toString().replace("\\", "/");
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!src.equals(dir)) {
                        String entryName = src.relativize(dir).toString().replace("\\", "/") + "/";
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return "压缩成功: " + zipFile;
    }

    private String decompress(Path zipFile, Path targetDir) throws IOException {
        if (!Files.exists(zipFile)) return "错误：ZIP 文件不存在 - " + zipFile;
        Files.createDirectories(targetDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = targetDir.resolve(entry.getName()).normalize();
                if (!outPath.startsWith(targetDir)) {
                    return "错误：ZIP 条目路径超出目标目录 - " + entry.getName();
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath);
                }
                zis.closeEntry();
            }
        }
        return "解压成功到: " + targetDir;
    }
}