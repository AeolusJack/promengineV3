package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@ToolHandler(
        name = "crypto",
        description = "哈希计算 (MD5, SHA1, SHA256) 和 Base64 编码/解码。",
        category = ToolHandler.Category.UTILITY,
        location = ToolHandler.Location.LOCAL,
        version = "1.0.0"
)
@SandboxPolicy(allowedPaths = {})
public class CryptoHandler {

    public String execute(
            @ToolParameter(value = "operation", description = "操作类型: md5, sha1, sha256, base64_encode, base64_decode")
            String operation,
            @ToolParameter(value = "input", description = "输入字符串")
            String input
    ) throws Exception {
        if (input == null) input = "";
        switch (operation.toLowerCase()) {
            case "md5":
                return md5(input);
            case "sha1":
                return sha1(input);
            case "sha256":
                return sha256(input);
            case "base64_encode":
                return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
            case "base64_decode":
                try {
                    byte[] decoded = Base64.getDecoder().decode(input);
                    return new String(decoded, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    return "Base64解码失败: " + e.getMessage();
                }
            default:
                return "不支持的操作: " + operation;
        }
    }

    private String md5(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    private String sha1(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    private String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}