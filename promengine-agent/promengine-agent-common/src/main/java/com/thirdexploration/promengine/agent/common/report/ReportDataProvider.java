package com.thirdexploration.promengine.agent.common.report;

import java.util.Map;

/**
 * 报告数据提供者接口，各垂类 Agent 可实现此接口，为报告提供专属数据。
 */
public interface ReportDataProvider {
    /** 数据源名称 */
    String getName();

    /** 收集报告所需数据 */
    Map<String, Object> collectData(Map<String, Object> params);
}