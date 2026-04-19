package com.thirdexploration.promengine.model.gateway.circuit;

import com.thirdexploration.promengine.model.config.ModelGatewayProperties;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
public class CircuitBreaker {

    private final String id;
    private final ModelGatewayProperties.CircuitBreakerConfig config;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
    private volatile Instant openUntil;

    public CircuitBreaker(String id, ModelGatewayProperties.CircuitBreakerConfig config) {
        this.id = id;
        this.config = config;
    }

    public <T> T execute(Supplier<T> supplier) throws Exception {
        State currentState = state.get();
        if (currentState == State.OPEN) {
            if (Instant.now().isAfter(openUntil)) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
                halfOpenSuccessCount.set(0);
                log.info("Circuit {} transitioned to HALF_OPEN", id);
            } else {
                throw new RuntimeException("Circuit breaker is OPEN");
            }
        }

        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    private void onSuccess() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            int success = halfOpenSuccessCount.incrementAndGet();
            if (success >= config.getHalfOpenMaxRequests()) {
                state.set(State.CLOSED);
                failureCount.set(0);
                log.info("Circuit {} closed", id);
            }
        } else if (current == State.CLOSED) {
            failureCount.set(0);
        }
    }

    private void onFailure() {
        int failures = failureCount.incrementAndGet();
        if (failures >= config.getFailureThreshold()) {
            state.set(State.OPEN);
            openUntil = Instant.now().plusSeconds(config.getTimeout());
            log.warn("Circuit {} opened until {}", id, openUntil);
        }
    }

    private enum State { CLOSED, OPEN, HALF_OPEN }
}