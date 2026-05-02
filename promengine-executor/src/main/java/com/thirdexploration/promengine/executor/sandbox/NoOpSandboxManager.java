package com.thirdexploration.promengine.executor.sandbox;

import com.thirdexploration.promengine.executor.tool.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@ConditionalOnMissingBean(value = SandboxManager.class, ignored = NoOpSandboxManager.class)
public class NoOpSandboxManager implements SandboxManager {

    private final Path workspaceRoot;

    public NoOpSandboxManager() {
        this.workspaceRoot = Paths.get("./sandbox-workspace").toAbsolutePath().normalize();
        try {
            java.nio.file.Files.createDirectories(workspaceRoot);
        } catch (Exception e) {
            log.warn("Failed to create workspace directory: {}", workspaceRoot);
        }
        log.info("NoOpSandboxManager initialized with workspace: {}", workspaceRoot);
    }

    @Override
    public Path resolve(String relativePath) throws SecurityException {
        Path target = workspaceRoot.resolve(relativePath).normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new SecurityException("Path traversal attempt: " + relativePath);
        }
        return target;
    }

    @Override
    public String executeInSandbox(String toolName, String jsonArgs) {
        log.warn("Sandbox is disabled, cannot execute tool '{}' in isolation", toolName);
        return "沙箱功能未启用，无法执行工具：" + toolName;
    }

    @Override
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    @Override
    public Path resolve(String relativePath, ToolDefinition.SandboxPolicyDef policy) throws SecurityException {
        // 策略为 null 时，退化为基本实现
        if (policy == null) {
            return resolve(relativePath);
        }
        // NoOp 模式下不执行严格策略，但仍可校验路径是否在工作区内
        return resolve(relativePath);
    }

    @Override
    public String executeInSandbox(String toolName, String jsonArgs, ToolDefinition.SandboxPolicyDef policy) {
        return executeInSandbox(toolName, jsonArgs);
    }
}