package com.thirdexploration.promengine.verifier;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.compiler.InterpreterFallback;
import com.dylibso.chicory.runtime.*;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.Value;
import com.thirdexploration.promengine.verifier.exception.WasmVerificationException;
import com.thirdexploration.promengine.verifier.model.IntentStructure;
import com.thirdexploration.promengine.verifier.model.VerificationResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 Chicory 纯 Java 运行时的 WebAssembly 形式化验证沙箱。
 * 
 * <p>Chicory 是一个 100% 纯 Java 实现的 WebAssembly 运行时，无需任何本地依赖或 JNI。
 * 它可以在任何能够运行 JVM 的环境下执行 Wasm 模块，并提供与 JVM 自身安全机制叠加的
 * "双重沙箱"效果（Wasm 隔离 + JVM 安全管控）。
 * 
 * <p>特性：
 * <ul>
 *   <li>纯 Java 实现，无 JNI 依赖，跨平台部署简单</li>
 *   <li>支持解释执行和运行时编译两种模式，编译模式可提供接近原生的执行速度</li>
 *   <li>可选的 WASI 支持，提供受控的文件系统访问能力</li>
 *   <li>与 PromEngine 的安全架构无缝集成</li>
 * </ul>
 *
 * @author Third Exploration
 * @since 1.0
 * @see <a href="https://chicory.dev/docs/">Chicory 官方文档</a>
 */
@Slf4j
@Component
public class WasmSandboxVerifier {

    private final VerifierProperties properties;
    
    /** Wasm 模块（编译后的中间表示） */
    private WasmModule module;
    
    /** 模块实例是否已初始化 */
    private volatile boolean initialized = false;
    
    /** 用于保证初始化线程安全的锁 */
    private final ReentrantLock initLock = new ReentrantLock();
    
    /** 上次加载的文件路径（用于热重载检测） */
    private Path lastLoadedPath;
    
    /** 上次加载的文件修改时间（用于热重载检测） */
    private long lastModifiedTime;
    
    public WasmSandboxVerifier(VerifierProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.isWasmEnabled()) {
            log.info("Wasm sandbox verifier is disabled");
            return;
        }
        loadModule();
    }
    
    /**
     * 加载 Wasm 模块。支持热重载：如果文件被修改，会重新加载。
     */
    private void loadModule() {
        initLock.lock();
        try {
            Path wasmPath = Path.of(properties.getWasmPath());
            if (!Files.exists(wasmPath)) {
                log.warn("Wasm module file not found: {}. Verification will fallback to passed.", wasmPath);
                initialized = false;
                return;
            }
            
            // 检查是否需要重新加载（热重载）
            long currentModified = wasmPath.toFile().lastModified();
            if (module != null && lastLoadedPath != null && 
                lastLoadedPath.equals(wasmPath) && 
                lastModifiedTime == currentModified) {
                // 文件未变化，无需重新加载
                return;
            }
            
            log.info("Loading Wasm module from: {}", wasmPath);
            byte[] wasmBytes = Files.readAllBytes(wasmPath);
            
            // 解析 Wasm 模块
            this.module = Parser.parse(wasmBytes);
            this.lastLoadedPath = wasmPath;
            this.lastModifiedTime = currentModified;
            this.initialized = true;
            
            log.info("Wasm module loaded successfully. Execution mode: {}, WASI: {}",
                    properties.getExecutionMode(), properties.isWasiEnabled());
        } catch (IOException e) {
            log.error("Failed to load Wasm module from {}", properties.getWasmPath(), e);
            initialized = false;
        } finally {
            initLock.unlock();
        }
    }

    /**
     * 验证意图结构体。
     * 
     * <p>Wasm 模块需要导出一个名为 "verify" 的函数，签名为：
     * (param $jsonPtr i32) (param $jsonLen i32) (result i32)
     * 
     * <p>该函数接收 JSON 字符串在 Wasm 线性内存中的指针和长度，
     * 返回 1 表示验证通过，0 表示验证失败。
     *
     * @param intent 待验证的意图结构体
     * @return 验证结果
     */
    public VerificationResult verify(IntentStructure intent) {
        if (!properties.isWasmEnabled()) {
            log.debug("Wasm verifier is disabled, passing through");
            return VerificationResult.passed();
        }

        if (!initialized || module == null) {
            log.warn("Wasm verifier not initialized, falling back to passed");
            return VerificationResult.passed();
        }

        checkAndReload();

        try {
            Instance instance = buildInstance();
            String json = intent.toJson();
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

            Memory memory = instance.memory();
            int ptr = allocateMemory(memory, jsonBytes.length);
            memory.write(ptr, jsonBytes);  // ✅ 修正方法名

            ExportFunction verifyFunc = instance.export("verify");
            if (verifyFunc == null) {
                log.error("Wasm module does not export 'verify' function");
                throw new WasmVerificationException("Missing required export: verify");
            }

            long[] results = verifyFunc.apply(ptr, jsonBytes.length);
            if (results == null || results.length == 0) {
                log.warn("Wasm verify function returned no result");
                return VerificationResult.blocked("Wasm verify function returned no result");
            }

            boolean passed = results[0] == 1;
            if (passed) {
                log.debug("Wasm verification passed for action: {}", intent.getAction());
                return VerificationResult.passed();
            } else {
                log.warn("Wasm verification failed for action: {}", intent.getAction());
                return VerificationResult.blocked("Wasm rule violation");
            }
        } catch (Exception e) {
            log.error("Wasm verification error for action: {}", intent.getAction(), e);
            if (properties.isCompilerFallbackEnabled()) {
                log.warn("Wasm execution failed, falling back to passed");
                return VerificationResult.passed();
            }
            return VerificationResult.blocked("Wasm execution error: " + e.getMessage());
        }
    }
    
    /**
     * 构建 Wasm 实例，根据配置选择解释执行或编译执行模式。
     */
    private Instance buildInstance() {
        Instance.Builder builder = Instance.builder(module);
        
        // 根据配置选择执行模式
        switch (properties.getExecutionMode()) {
            case COMPILER:
                if (properties.isCompilerFallbackEnabled()) {
                    // 编译失败时回退到解释执行
                    builder.withMachineFactory(
                        MachineFactoryCompiler.builder(module)
                            .withInterpreterFallback(InterpreterFallback.WARN)
                            .compile()
                    );
                } else {
                    // 编译失败时抛出异常
                    builder.withMachineFactory(
                        MachineFactoryCompiler.builder(module)
                            .withInterpreterFallback(InterpreterFallback.FAIL)
                            .compile()
                    );
                }
                log.debug("Using Chicory compiler mode for Wasm execution");
                break;
            case INTERPRETER:
            default:
                // 使用默认的解释执行模式
                log.debug("Using Chicory interpreter mode for Wasm execution");
                break;
        }
        // 创建 Store 用于管理主机函数
        Store store = new Store();

        // 如果启用了 WASI 支持，添加 WASI 主机函数
        // 修改点：将 WASI 的主机函数添加到 Store 中，而不是 builder
        if (properties.isWasiEnabled()) {
            WasiOptions wasiOptions = WasiOptions.builder()
                    .withStdout(System.out)
                    .withStderr(System.err)
                    .build();
            WasiPreview1 wasi = WasiPreview1.builder()
                    .withOptions(wasiOptions)
                    .build();
            store.addFunction(wasi.toHostFunctions()); // 将 HostFunction 添加到 Store
            log.debug("WASI support enabled for Wasm module");
        }
        
        return builder.build();
    }
    
    /**
     * 在 Wasm 线性内存中分配空间。
     * 简化实现：在内存末尾追加。
     */
    private int allocateMemory(Memory memory, int size) {
        // 简单实现：使用当前内存大小作为起始地址
        // 实际生产中可能需要更复杂的内存管理
        int currentSize = memory.pages() * 65536; // 每页 64KB
        if (currentSize < size + 1024) {
            // 需要扩容
            int requiredPages = (size + 1024 + 65535) / 65536;
            memory.grow(requiredPages - memory.pages());
        }
        return currentSize;
    }
    
    /**
     * 检查 Wasm 文件是否被更新，如果是则重新加载。
     */
    private void checkAndReload() {
        if (!properties.isWasmEnabled() || lastLoadedPath == null) {
            return;
        }
        try {
            long currentModified = lastLoadedPath.toFile().lastModified();
            if (currentModified != lastModifiedTime) {
                log.info("Wasm module file changed, reloading...");
                loadModule();
            }
        } catch (Exception e) {
            log.debug("Failed to check Wasm module modification time", e);
        }
    }
    
    /**
     * 手动重新加载 Wasm 模块。
     * 可通过 JMX 或管理 API 调用。
     */
    public void reload() {
        if (!properties.isWasmEnabled()) {
            log.info("Wasm verifier is disabled, reload ignored");
            return;
        }
        loadModule();
    }
    
    /**
     * 获取当前状态信息。
     */
    public VerifierStatus getStatus() {
        return new VerifierStatus(
            properties.isWasmEnabled(),
            initialized,
            properties.getExecutionMode().name(),
            lastLoadedPath != null ? lastLoadedPath.toString() : null,
            lastModifiedTime
        );
    }
    
    /**
     * 验证器状态快照。
     */
    public record VerifierStatus(
        boolean enabled,
        boolean initialized,
        String executionMode,
        String modulePath,
        long moduleLastModified
    ) {}
    
    @PreDestroy
    public void cleanup() {
        // Chicory 不需要显式清理资源，由 GC 自动回收
        log.debug("WasmSandboxVerifier cleanup completed");
    }
}