package com.thirdexploration.promengine.runtime.dto;

import com.thirdexploration.promengine.neuro.web.TopEvent;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Builder
@Data
public class GroupChatEvent extends TopEvent {

    private String sessionId;
    private String agentId;
    private String text;
    private String type; //事件消息类型
}
