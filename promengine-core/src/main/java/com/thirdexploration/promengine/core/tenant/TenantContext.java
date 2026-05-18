package com.thirdexploration.promengine.core.tenant;

public final class TenantContext {
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantId) {
        currentTenant.set(tenantId);
    }

    public static String get() {
        return currentTenant.get();
    }

    public static String getOrDefault() {
        String tenant = currentTenant.get();
        return tenant != null ? tenant : "default";
    }

    public static void clear() {
        currentTenant.remove();
    }
}