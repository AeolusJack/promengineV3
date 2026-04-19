package com.thirdexploration.promengine.verifier.exception;

import com.thirdexploration.promengine.core.exception.PromEngineException;

/**
 * Wasm 沙箱验证异常。
 */
public class WasmVerificationException extends PromEngineException {
    
    public WasmVerificationException(String message) {
        super(message);
    }
    
    public WasmVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}