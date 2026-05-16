package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.management.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@ToolHandler(
        name = "system_info",
        description = "获取当前系统的关键环境信息，包括操作系统、Java版本、工作目录、PATH、环境变量等，便于判断可用的命令和软件。",
        category = ToolHandler.Category.INFORMATION,
        location = ToolHandler.Location.LOCAL,
        version = "1.2.0"
)
@SandboxPolicy(
        allowedPaths = {},
        maxExecutionSeconds = 5
)
@Component
public class SystemInfoHandler {

    public String execute(
            @ToolParameter(value = "verbose", description = "是否输出详细运行时信息（内存池、GC、线程等），默认false", required = false, example = "false")
            Boolean verbose,
            @ToolParameter(value = "include_system_properties", description = "是否包含所有系统属性（默认false，避免过多输出）", required = false, example = "false")
            Boolean includeSystemProps
    ) {
        boolean verboseFlag = verbose != null && verbose;
        boolean propsFlag = includeSystemProps != null && includeSystemProps;

        Map<String, Object> info = new HashMap<>();

        // 1. 操作系统信息
        info.put("os", getOsInfo());

        // 2. Java 基本信息
        info.put("java", getJavaInfo());

        // 3. 路径相关
        info.put("paths", getPathInfo());

        // 4. 运行时基础信息
        info.put("runtime_basic", getRuntimeBasicInfo());

        // 5. 环境变量（默认输出，过滤敏感信息）
        info.put("environment_variables", getEnvironmentVariables());

        // 6. 详细运行时信息（可选）
        if (verboseFlag) {
            info.put("runtime_detailed", getRuntimeDetailedInfo());
        }

        // 7. 系统属性（可选，默认不输出）
        if (propsFlag) {
            info.put("system_properties", getSystemProperties());
        }

        return formatOutput(info);
    }

    private Map<String, String> getOsInfo() {
        Map<String, String> os = new HashMap<>();
        os.put("name", System.getProperty("os.name"));
        os.put("version", System.getProperty("os.version"));
        os.put("arch", System.getProperty("os.arch"));
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            os.put("family", "windows");
            os.put("path_separator", ";");
            os.put("file_separator", "\\");
        } else if (osName.contains("mac")) {
            os.put("family", "macos");
            os.put("path_separator", ":");
            os.put("file_separator", "/");
        } else {
            os.put("family", "unix");
            os.put("path_separator", ":");
            os.put("file_separator", "/");
        }
        return os;
    }

    private Map<String, String> getJavaInfo() {
        Map<String, String> java = new HashMap<>();
        java.put("version", System.getProperty("java.version"));
        java.put("vendor", System.getProperty("java.vendor"));
        java.put("home", System.getProperty("java.home"));
        return java;
    }

    private Map<String, String> getPathInfo() {
        Map<String, String> paths = new HashMap<>();
        paths.put("user_dir", System.getProperty("user.dir"));
        paths.put("user_home", System.getProperty("user.home"));
        paths.put("tmp_dir", System.getProperty("java.io.tmpdir"));
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) pathEnv = System.getenv("Path");
        paths.put("PATH", pathEnv != null ? pathEnv : "(not available)");
        return paths;
    }

    private Map<String, Object> getRuntimeBasicInfo() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> basic = new HashMap<>();
        basic.put("available_processors", runtime.availableProcessors());
        basic.put("free_memory_mb", runtime.freeMemory() / (1024 * 1024));
        basic.put("total_memory_mb", runtime.totalMemory() / (1024 * 1024));
        basic.put("max_memory_mb", runtime.maxMemory() / (1024 * 1024));
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double loadAvg = osBean.getSystemLoadAverage();
        basic.put("system_load_average", loadAvg >= 0 ? loadAvg : "N/A");
        return basic;
    }

    private Map<String, Object> getRuntimeDetailedInfo() {
        Map<String, Object> detailed = new HashMap<>();
        java.util.List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        Map<String, String> memoryPools = new HashMap<>();
        for (MemoryPoolMXBean pool : pools) {
            long used = pool.getUsage().getUsed() / (1024 * 1024);
            long max = pool.getUsage().getMax();
            memoryPools.put(pool.getName(), used + "MB / " + (max == -1 ? "unlimited" : max / (1024 * 1024) + "MB"));
        }
        detailed.put("memory_pools", memoryPools);
        java.util.List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        Map<String, Long> gcStats = new HashMap<>();
        for (GarbageCollectorMXBean gc : gcBeans) {
            gcStats.put(gc.getName() + "_count", gc.getCollectionCount());
            gcStats.put(gc.getName() + "_time_ms", gc.getCollectionTime());
        }
        detailed.put("gc_stats", gcStats);
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        detailed.put("thread_count", threadBean.getThreadCount());
        detailed.put("peak_thread_count", threadBean.getPeakThreadCount());
        return detailed;
    }

    private Map<String, String> getEnvironmentVariables() {
        Map<String, String> env = System.getenv();
        Map<String, String> filtered = new HashMap<>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            String lowerKey = key.toLowerCase();
            // 过滤明显的敏感信息，但保留PATH、HOME等
            if (!lowerKey.contains("password") && !lowerKey.contains("secret")
                    && !lowerKey.contains("key") && !lowerKey.contains("token")
                    && !lowerKey.contains("credential")) {
                filtered.put(key, entry.getValue());
            } else {
                filtered.put(key, "***HIDDEN***");
            }
        }
        return filtered;
    }

    private Map<String, String> getSystemProperties() {
        Properties props = System.getProperties();
        Map<String, String> map = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            String lowerKey = key.toLowerCase();
            if (!lowerKey.contains("password") && !lowerKey.contains("secret")
                    && !lowerKey.contains("key") && !lowerKey.contains("token")
                    && !lowerKey.contains("credential")) {
                map.put(key, props.getProperty(key));
            } else {
                map.put(key, "***HIDDEN***");
            }
        }
        return map;
    }

    private String formatOutput(Map<String, Object> info) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : info.entrySet()) {
            sb.append("【").append(entry.getKey()).append("】\n");
            Object val = entry.getValue();
            if (val instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) val;
                for (Map.Entry<?, ?> sub : map.entrySet()) {
                    sb.append("  ").append(sub.getKey()).append(": ").append(sub.getValue()).append("\n");
                }
            } else {
                sb.append("  ").append(val).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}