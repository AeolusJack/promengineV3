package com.thirdexploration.promengine.core.context;

import com.thirdexploration.promengine.core.tenant.TenantContext;
import java.util.Collections;
import java.util.List;

/**
 * 当前用户的可见性上下文：用户ID、所属团队列表、当前租户ID。
 * 后续可从数据库加载团队列表，当前通过请求头或JWT获取。
 */
public class VisibilityContext {
    private static final ThreadLocal<VisibilityContext> current = new ThreadLocal<>();

    private final String userId;
    private final List<String> teamIds;
    private final String tenantId;

    public VisibilityContext(String userId, List<String> teamIds, String tenantId) {
        this.userId = userId;
        this.teamIds = teamIds != null ? teamIds : Collections.emptyList();
        this.tenantId = tenantId != null ? tenantId : "default";
    }

    public static void set(VisibilityContext context) {
        current.set(context);
    }

    public static VisibilityContext get() {
        VisibilityContext ctx = current.get();
        if (ctx == null) {
            // 返回一个空的上下文，防止 NPE，但实际应在请求入口设置
            return new VisibilityContext(null, Collections.emptyList(), TenantContext.getOrDefault());
        }
        return ctx;
    }

    public static void clear() {
        current.remove();
    }

    public String getUserId() { return userId; }
    public List<String> getTeamIds() { return teamIds; }
    public String getTenantId() { return tenantId; }
}