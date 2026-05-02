package com.thirdexploration.promengine.core;

import com.thirdexploration.promengine.core.domain.ConfigFieldMeta;
import com.thirdexploration.promengine.core.domain.ConfigUpdateResult;
import com.thirdexploration.promengine.core.domain.UserConfigView;

import java.util.List;
import java.util.Map;

/**
 * 配置管理服务，提供前端可修改配置项的查询、更新与回滚。
 */
public interface ConfigManagementService {

    /**
     * 获取当前用户配置视图。
     */
    UserConfigView getUserConfig(String userId);

    /**
     * 批量更新配置项。
     */
    ConfigUpdateResult updateConfig(String userId, Map<String, Object> updates);

    /**
     * 批准待审批的变更（例如配额调整）。
     */
    ConfigUpdateResult approvePendingChange(String userId, String changeId);

    /**
     * 回滚到指定版本。
     */
    UserConfigView rollback(String userId, String targetVersion);

    /**
     * 获取所有配置项的元数据（用于前端动态表单生成）。
     */
    List<ConfigFieldMeta> getConfigMetadata();

    void resetToDefault(String userId);
}