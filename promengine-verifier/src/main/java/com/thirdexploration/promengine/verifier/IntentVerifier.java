package com.thirdexploration.promengine.verifier;

import com.thirdexploration.promengine.verifier.model.IntentStructure;
import com.thirdexploration.promengine.verifier.model.VerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 意图验证门面，先过配置化过滤器，再走 Wasm 沙箱。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentVerifier {

    private final IntentFilterEngine filterEngine;
    private final WasmSandboxVerifier sandboxVerifier;
    private final VerifierProperties properties;

    public VerificationResult verify(IntentStructure intent) {
        // 第一层：配置化过滤器
        VerificationResult filterResult = filterEngine.evaluate(intent);
        if (!filterResult.isPassed()) {
            log.warn("Intent blocked by filter: {}", filterResult.getReason());
            return filterResult;
        }

        // 第二层：形式化验证（仅对 CRITICAL 操作）
        if (properties.isWasmEnabled() && "CRITICAL".equals(intent.getDelegationLevel())) {
            VerificationResult wasmResult = sandboxVerifier.verify(intent);
            if (!wasmResult.isPassed()) {
                log.error("Intent blocked by Wasm sandbox: {}", wasmResult.getReason());
                return wasmResult;
            }
        }

        return VerificationResult.passed();
    }
}