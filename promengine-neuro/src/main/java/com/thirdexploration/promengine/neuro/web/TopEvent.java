package com.thirdexploration.promengine.neuro.web;

import lombok.Builder;
import lombok.Data;

//事件根
@Data
public class TopEvent {
    //必须重写事件类型
    private String type; //事件消息类型
}
