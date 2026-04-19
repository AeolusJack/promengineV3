package com.thirdexploration.promengine.neuro;

import com.thirdexploration.promengine.neuro.web.RippleWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ThinkingRippleGenerator {

    private final RippleWebSocketHandler webSocketHandler;
    private final NeuroProperties properties;

    public void generate(double entropy) {
        if (!properties.isThinkingRippleEnabled()) return;

        RippleEvent event = new RippleEvent(
                entropy,
                entropy > 0.7 ? "red" : "green",
                System.currentTimeMillis()
        );
        webSocketHandler.broadcast(event);
    }

    public record RippleEvent(double entropy, String color, long timestamp) {}
}