package com.thirdexploration.promengine.executor.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.concurrent.atomic.AtomicReference;

@Getter
public class StreamCompletedEvent extends ApplicationEvent {
    private final String executionId;
    private final String sessionId;
    private final String userId;
    private final String finalAnswer;

    public StreamCompletedEvent(Object source, String executionId, String sessionId, String userId, String finalAnswer) {
        super(source);
        this.executionId = executionId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.finalAnswer = finalAnswer;
    }
}