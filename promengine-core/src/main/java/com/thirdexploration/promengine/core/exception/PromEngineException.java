package com.thirdexploration.promengine.core.exception;

/**
 * PromEngine 基础异常。
 */
public class PromEngineException extends RuntimeException {
    public PromEngineException(String message) {
        super(message);
    }

    public PromEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}