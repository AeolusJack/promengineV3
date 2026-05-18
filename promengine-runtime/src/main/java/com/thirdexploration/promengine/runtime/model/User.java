package com.thirdexploration.promengine.runtime.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class User {
    private String id;
    private String tenantId;
    private String username;
    private String password;   // 加密存储
    private String nickname;
    private String avatar;
    private boolean enabled;
    private long createdAt;
}