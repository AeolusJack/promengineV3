package com.thirdexploration.promengine.executor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.orchestrator.sandbox")
public class SandboxProperties {
    private boolean enabled = true;
    private Type type = Type.WASM;
    private String wasmModulesPath = "./wasm-modules/";
    private String workspacePath = "./sandbox-workspace/";

    public enum Type { WASM, DOCKER, DISABLED }
}