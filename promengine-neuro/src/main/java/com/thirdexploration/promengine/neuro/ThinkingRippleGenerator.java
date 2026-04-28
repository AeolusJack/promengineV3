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
        //思维涟漪事件
        RippleEvent event = new RippleEvent("ripple",
                entropy,
                entropy > 0.7 ? "red" : "green",
                System.currentTimeMillis()
        );
        webSocketHandler.broadcast(event);
    }

    public record RippleEvent(String type,double entropy, String color, long timestamp) {}
}