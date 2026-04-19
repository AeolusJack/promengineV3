package com.thirdexploration.promengine.verifier;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 验证器配置属性，对应 application.yml 中的 promengine.security.verifier 节点。
 */
@Data
@Component
@ConfigurationProperties(prefix = "promengine.security.verifier")
public class VerifierProperties {
    
    /** 是否启用验证器 */
    private boolean enabled = true;
    
    /** Wasm 沙箱模块文件路径 */
    private String wasmPath = "/etc/promengine/verifier.wasm";
    
    /** 是否启用 Wasm 沙箱验证 */
    private boolean wasmEnabled = false;
    
    /** 是否启用配置化意图过滤器 */
    private boolean intentFiltersEnabled = true;
    
    /** Chicory 执行模式：INTERPRETER（解释执行）或 COMPILER（编译执行，更快） */
    private ExecutionMode executionMode = ExecutionMode.COMPILER;
    
    /** 是否启用 WASI 支持（允许沙箱内有限的文件系统访问） */
    private boolean wasiEnabled = false;
    
    /** 是否允许编译失败时回退到解释执行 */
    private boolean compilerFallbackEnabled = true;
    
    /** 虚拟文件系统根路径（仅当 wasiEnabled=true 时有效） */
    private String virtualFsRoot = "/tmp/promengine/wasi";
    
    public enum ExecutionMode {
        /** 解释执行，兼容性最好，速度较慢 */
        INTERPRETER,
        /** 编译为 JVM 字节码执行，速度更快，适合生产环境 */
        COMPILER,
        /** 构建时预编译，速度最快，但需要提前编译 Wasm 模块 */
        AOT
    }
}