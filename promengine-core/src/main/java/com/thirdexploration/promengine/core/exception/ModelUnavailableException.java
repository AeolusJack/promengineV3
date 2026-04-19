package com.thirdexploration.promengine.core.exception;

public class ModelUnavailableException extends PromEngineException {
    public ModelUnavailableException(String modelId) {
        super("Model unavailable: " + modelId);
    }
}