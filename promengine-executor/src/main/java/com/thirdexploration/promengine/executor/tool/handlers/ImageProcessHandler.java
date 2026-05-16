package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.sandbox.SandboxManager;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@ToolHandler(
        name = "image_process",
        description = "图片处理：缩放、格式转换 (支持 jpg/png/gif/bmp)。输入文件必须在沙箱内，输出也保存在沙箱内。",
        category = ToolHandler.Category.MEDIA,
        location = ToolHandler.Location.SANDBOX,
        version = "1.0.0"
)
@SandboxPolicy(allowedPaths = {"documents", "projects", "downloads", "tmp"})
public class ImageProcessHandler {

    @Autowired
    private SandboxManager sandboxManager;

    public String execute(
            @ToolParameter(value = "input_path", description = "输入图片路径（沙箱内）", example = "documents/photo.jpg")
            String inputPath,
            @ToolParameter(value = "output_path", description = "输出图片路径（沙箱内）", example = "projects/thumb.png")
            String outputPath,
            @ToolParameter(value = "width", description = "目标宽度（像素）", required = false)
            Integer width,
            @ToolParameter(value = "height", description = "目标高度（像素）", required = false)
            Integer height,
            @ToolParameter(value = "format", description = "输出格式：jpg, png, gif, bmp（默认根据扩展名推断）", required = false)
            String format
    ) throws Exception {
        Path in = sandboxManager.resolve(inputPath);
        Path out = sandboxManager.resolve(outputPath);
        if (!Files.exists(in)) return "输入文件不存在: " + inputPath;

        BufferedImage img = ImageIO.read(in.toFile());
        if (img == null) return "无法读取图片，可能格式不支持或文件损坏";

        // 缩放
        if (width != null && height != null) {
            Image scaled = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            BufferedImage newImg = new BufferedImage(width, height, img.getType());
            Graphics2D g = newImg.createGraphics();
            g.drawImage(scaled, 0, 0, null);
            g.dispose();
            img = newImg;
        } else if (width != null || height != null) {
            // 按比例缩放
            int newW, newH;
            if (width != null) {
                newW = width;
                newH = (int) ((double) img.getHeight() / img.getWidth() * width);
            } else {
                newH = height;
                newW = (int) ((double) img.getWidth() / img.getHeight() * height);
            }
            Image scaled = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
            BufferedImage newImg = new BufferedImage(newW, newH, img.getType());
            Graphics2D g = newImg.createGraphics();
            g.drawImage(scaled, 0, 0, null);
            g.dispose();
            img = newImg;
        }

        // 确定输出格式
        String fmt = (format != null) ? format.toLowerCase() : getExtension(out.getFileName().toString());
        if (!isSupportedFormat(fmt)) fmt = "png";

        // 写入
        Files.createDirectories(out.getParent());
        try (OutputStream os = Files.newOutputStream(out)) {
            ImageIO.write(img, fmt, os);
        }
        return "图片处理成功，保存至: " + outputPath;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0) ? filename.substring(dot + 1).toLowerCase() : "png";
    }

    private boolean isSupportedFormat(String fmt) {
        return fmt.equals("jpg") || fmt.equals("jpeg") || fmt.equals("png") || fmt.equals("gif") || fmt.equals("bmp");
    }
}