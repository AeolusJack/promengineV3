package com.thirdexploration.promengine.neuro.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.neuro.ThinkingRippleGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RippleWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 改用 Map 存储，key 为 sessionId，value 为对应的 WebSocket 会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);
        if (sessionId != null) {
            sessions.put(sessionId, session);
            log.debug("WebSocket connected: sessionId={}, id={}", sessionId, session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = extractSessionId(session);
        if (sessionId != null) {
            sessions.remove(sessionId);
            log.debug("WebSocket disconnected: sessionId={}", sessionId);
        }
    }

    /**
     * 从 WebSocket 连接 URI 中提取 sessionId 参数
     */
    private String extractSessionId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && "sessionId".equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }

    /**
     * 向指定 sessionId 的 WebSocket 连接推送涟漪事件
     */
    public void sendToSession(String sessionId, ThinkingRippleGenerator.RippleEvent event) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(event);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Failed to send ripple event to session {}", sessionId, e);
        }
    }

    // 保留原有的 broadcast 方法，用于调试或全局推送
    public void broadcast(ThinkingRippleGenerator.RippleEvent event) {
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            try {
                if (entry.getValue().isOpen()) {
                    String json = objectMapper.writeValueAsString(event);
                    entry.getValue().sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                log.error("Failed to broadcast ripple event", e);
            }
        }
    }

    public void sendToSession(String sessionId, TopEvent topEvent) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(topEvent);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Failed to send ripple event to session {}", sessionId, e);
        }
    }

    public void sendToSession(String sessionId, String json) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Failed to send ripple event to session {}", sessionId, e);
        }
    }
}