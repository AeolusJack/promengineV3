package com.thirdexploration.promengine.web.config;

import com.thirdexploration.promengine.neuro.web.RippleWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RippleWebSocketHandler rippleHandler;

    public WebSocketConfig(RippleWebSocketHandler rippleHandler) {
        this.rippleHandler = rippleHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(rippleHandler, "/ws/ripple")
                .setAllowedOrigins("*");
    }
}