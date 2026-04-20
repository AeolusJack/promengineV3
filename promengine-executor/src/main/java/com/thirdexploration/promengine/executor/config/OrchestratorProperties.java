package com.thirdexploration.promengine.executor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@ConfigurationProperties(prefix = "promengine.orchestrator")
@Component
public class OrchestratorProperties {
    private Mode mode = Mode.SIMPLE;            // SIMPLE 或 REACT
    private int maxSteps = 10;                  // ReAct 最大循环步数
    private boolean toolUseEnabled = true;      // 是否允许工具调用
    private SandboxConfig sandbox = new SandboxConfig();

    public enum Mode { SIMPLE, REACT }

    @Data
    public static class SandboxConfig {
        private boolean enabled = true;
        private Type type = Type.WASM;
        private String wasmModulesPath = "./wasm-modules/";
        private String workspacePath = "./sandbox-workspace/";

        public enum Type { WASM, DOCKER, DISABLED }
    }
}