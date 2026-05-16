package com.thirdexploration.promengine.agent.common.report;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ReportTemplate {
    /** 模板唯一标识 */
    private String id;
    /** 模板名称 */
    private String name;
    /** 报告类型：MARKDOWN, HTML, JSON */
    private ReportType type;
    /** 提示词模板，支持变量占位符 {{variable}} */
    private String promptTemplate;

    public enum ReportType {
        MARKDOWN, HTML, JSON
    }
}