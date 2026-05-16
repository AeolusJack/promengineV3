package com.thirdexploration.promengine.executor.sandbox;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.thirdexploration.promengine.executor.config.SandboxProperties;
import com.thirdexploration.promengine.executor.tool.registry.ToolDefinition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 基于 Chicory (纯 Java Wasm 运行时) 的沙箱管理器实现。
 * 通过配置文件中的 sandbox.type=WASM 激活。
 *
 * 工作原理：
 * 1. 启动时扫描 wasm-modules-path 目录下的所有 .wasm 文件。
 * 2. 每个 Wasm 模块对应一个工具，文件名（不含扩展名）即为工具名。
 * 3. 执行工具时，调用对应 Wasm 模块导出的 "execute" 函数，传入 JSON 参数。
 *
 * 安全边界：
 * - 所有文件操作必须在 Wasm 模块内部通过 WASI 完成，且受限于工作区路径。
 * - resolve 方法强制校验路径，防止沙箱外访问。
 */

import com.dylibso.chicory.runtime.*;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;

/**
 * 基于 Chicory 1.5.3 的 Wasm 沙箱管理器实现。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "promengine.orchestrator.sandbox.type", havingValue = "WASM")
public class WasmSandboxManager implements SandboxManager {

    private final Path workspaceRoot;
    private final Path wasmModulesPath;
    private final Map<String, Instance> toolInstances = new ConcurrentHashMap<>();

    public WasmSandboxManager(SandboxProperties properties) throws IOException {
        this.workspaceRoot = Paths.get(properties.getWorkspacePath()).toAbsolutePath().normalize();
        this.wasmModulesPath = Paths.get(properties.getWasmModulesPath()).toAbsolutePath().normalize();
        Files.createDirectories(workspaceRoot);
        log.info("WasmSandboxManager initialized with workspace: {}, modules: {}", workspaceRoot, wasmModulesPath);
    }

    @PostConstruct
    public void preloadModules() {
        if (!Files.exists(wasmModulesPath)) {
            log.warn("Wasm modules directory not found: {}", wasmModulesPath);
            return;
        }
        try (var stream = Files.list(wasmModulesPath)) {
            stream.filter(p -> p.toString().endsWith(".wasm"))
                    .forEach(this::loadModule);
        } catch (IOException e) {
            log.error("Failed to scan Wasm modules directory", e);
        }
    }

    private void loadModule(Path wasmFile) {
        try {
            // Chicory 1.5.3 正确用法：Parser.parse() → WasmModule → Instance.builder().build()
            WasmModule module = Parser.parse(wasmFile.toFile());
            Instance instance = Instance.builder(module).build();
            String toolName = wasmFile.getFileName().toString().replace(".wasm", "");
            toolInstances.put(toolName, instance);
            log.info("Loaded Wasm module for tool: {}", toolName);
        } catch (Exception e) {
            log.error("Failed to load Wasm module: {}", wasmFile, e);
        }
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
        // 使用默认策略（不限制）调用带策略的方法
        return executeInSandbox(toolName, jsonArgs, null);
    }

    @Override
    public String executeInSandbox(String toolName, String jsonArgs, ToolDefinition.SandboxPolicyDef policy) {
        // 1. 参数校验
        if (toolName == null || toolName.isBlank()) {
            return "错误：工具名称不能为空";
        }
        if (jsonArgs == null) {
            jsonArgs = "{}";
        }

        // 2. 获取 Wasm 实例
        Instance instance = toolInstances.get(toolName);
        if (instance == null) {
            log.warn("Wasm module not found for tool: {}", toolName);
            return "错误：未找到对应的 Wasm 模块: " + toolName;
        }

        // 3. 获取策略中的限制（如果策略为 null 则使用默认宽松值）
        int maxExecutionSeconds = (policy != null) ? policy.getMaxExecutionSeconds() : 30;
        int maxMemoryMB = (policy != null) ? policy.getMaxMemoryMB() : 64;

        // 4. 准备参数数据
        byte[] jsonBytes = jsonArgs.getBytes(StandardCharsets.UTF_8);
        Memory memory = instance.memory();

        // 确保线性内存至少有 64KB 空闲空间（假设从偏移 1024 开始写入）
        final int DATA_OFFSET = 1024;
        int requiredBytes = DATA_OFFSET + jsonBytes.length;
        int currentPages = memory.pages();
        int neededPages = (requiredBytes + 65535) / 65536; // 向上取整
        if (neededPages > currentPages) {
            int growBy = neededPages - currentPages;
            memory.grow(growBy);
            log.debug("Grew memory by {} pages for tool {}", growBy, toolName);
        }

        // 检查内存限制
        if (memory.pages() * 64 > maxMemoryMB * 1024) {
            return "错误：执行所需内存超过策略限制 (" + maxMemoryMB + "MB)";
        }

        try {
            // 写入参数
            memory.write(DATA_OFFSET, jsonBytes);

            // 获取导出函数
            ExportFunction execute = instance.export("execute");
            if (execute == null) {
                return "错误：Wasm 模块未导出 'execute' 函数";
            }

            // 5. 带超时执行
            ExecutorService executor = Executors.newSingleThreadExecutor();
            java.util.concurrent.Future<String> future = executor.submit(() -> {
                // 调用 execute 函数，约定签名: (ptr: i32, len: i32) -> i32 (结果指针)
                long[] results = execute.apply(DATA_OFFSET, jsonBytes.length);
                if (results == null || results.length == 0) {
                    return "错误：Wasm 函数未返回结果";
                }
                long resultPtr = results[0];

                // 读取结果字符串（以 null 结尾，最大读取 10KB 防止越界）
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int offset = (int) resultPtr;
                int maxLength = 10 * 1024;
                for (int i = 0; i < maxLength; i++) {
                    byte b = memory.read(offset++);
                    if (b == 0) break;
                    baos.write(b);
                }
                return baos.toString(StandardCharsets.UTF_8);
            });

            try {
                return future.get(maxExecutionSeconds, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                log.warn("Wasm execution timeout for tool: {}", toolName);
                return "沙箱执行超时（" + maxExecutionSeconds + "秒）";
            } finally {
                executor.shutdownNow();
            }
        } catch (Exception e) {
            log.error("Wasm sandbox execution failed for tool: {}", toolName, e);
            return "沙箱执行失败：" + e.getMessage();
        }
    }

    @Override
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    @Override
    public Path resolve(String relativePath, ToolDefinition.SandboxPolicyDef policy) throws SecurityException {
        // 1. 不允许空或空白相对路径
        if (relativePath == null || relativePath.isBlank()) {
            throw new SecurityException("Relative path must not be blank");
        }

        // 2. 策略为 null 时，退化为基本实现
        if (policy == null) {
            return resolve(relativePath);
        }

        // 3. 获取允许的路径列表，若未配置则视同无限制
        List<String> allowed = policy.getAllowedPaths();
        if (allowed == null || allowed.isEmpty()) {
            return resolve(relativePath);
        }

        // 4. 解析目标路径
        Path target = workspaceRoot.resolve(relativePath).normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new SecurityException("Path traversal attempt: " + relativePath);
        }

        // 5. 检查目标路径是否以任一允许的工作子路径开头
        for (String allow : allowed) {
            Path allowedPath = workspaceRoot.resolve(allow).normalize();
            if (target.startsWith(allowedPath)) {
                return target;
            }
        }

        throw new SecurityException("Path not allowed by sandbox policy: " + relativePath
                + ". Allowed roots: " + policy.getAllowedPaths()
                + ". Please use a path starting with one of these directories, e.g., 'documents/" + relativePath + "'.");
    }
}