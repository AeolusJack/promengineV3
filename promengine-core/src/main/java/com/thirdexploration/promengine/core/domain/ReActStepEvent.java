package com.thirdexploration.promengine.core.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
@Builder
public class ReActStepEvent {

    private String type;        // THINKING, TOOL_CALL, TOOL_RESULT, RETRY, ERROR, COMPLETE

//    @JsonSerialize(using = AtomicIntegerSerializer.class)
    private int stepNumber;
    private String executionId; // 新增：关联的执行ID
    private String description; // 描述信息
    private String detail;      // 详细信息（工具名、参数、输出等）
    private String status;      // RUNNING, SUCCESS, FAILED
    private long timestamp;
}