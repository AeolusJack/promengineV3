package com.thirdexploration.promengine.core.exception;

public class QuotaExceededException extends PromEngineException {
    public QuotaExceededException(String message) {
        super(message);
    }
}