package com.thirdexploration.promengine.memory.exception;

import com.thirdexploration.promengine.core.exception.PromEngineException;

public class MemoryStorageException extends PromEngineException {
    public MemoryStorageException(String message) {
        super(message);
    }
    public MemoryStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}