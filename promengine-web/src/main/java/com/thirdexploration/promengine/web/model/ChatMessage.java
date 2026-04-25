package com.thirdexploration.promengine.web.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessage {
    private String id;
    private String userId;
    private String sessionId;
    private String sessionName;   // 新增
    private String role;        // user / assistant / system
    private String content;
    private long timestamp;
    private long createdAt;
}