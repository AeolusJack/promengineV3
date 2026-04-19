package com.thirdexploration.promengine.apex;

import com.thirdexploration.promengine.core.ApexController.CircuitBreakerState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CircuitBreakerManager {

    private final Map<String, CircuitBreakerState> states = new ConcurrentHashMap<>();

    public CircuitBreakerState getState(String providerId) {
        return states.getOrDefault(providerId, CircuitBreakerState.CLOSED);
    }

    public void updateState(String providerId, CircuitBreakerState state) {
        states.put(providerId, state);
    }
}