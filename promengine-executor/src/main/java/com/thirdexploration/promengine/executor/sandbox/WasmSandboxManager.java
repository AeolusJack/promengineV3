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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        return "";
    }

    @Override
    public String executeInSandbox(String toolName, String jsonArgs, ToolDefinition.SandboxPolicyDef policy) {
        Instance instance = toolInstances.get(toolName);
        // ... 加载模块

        try {
            Memory memory = instance.memory();
            byte[] jsonBytes = jsonArgs.getBytes(StandardCharsets.UTF_8);
            // 简单处理：直接在内存起始地址写入数据（假设模块预留了足够空间）
            memory.write(0, jsonBytes);

            ExportFunction execute = instance.export("execute");
            // 假设 execute 函数签名：(ptr: i32, len: i32) -> i32
            long resultPtr = execute.apply(0, jsonBytes.length)[0];

            // 读取结果字符串（以 null 结尾）
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            int offset = (int) resultPtr;
            byte b;
            while ((b = memory.read(offset++)) != 0) {
                baos.write(b);
            }

            return baos.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Wasm execution failed", e);
            return "沙箱执行失败：" + e.getMessage();
        }
    }

    @Override
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    @Override
    public Path resolve(String relativePath, ToolDefinition.SandboxPolicyDef policy) throws SecurityException {
        return null;
    }
}