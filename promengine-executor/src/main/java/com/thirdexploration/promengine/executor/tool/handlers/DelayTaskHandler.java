package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.*;

@ToolHandler(
        name = "delay_task",
        description = "延迟执行一个命令或脚本（仅一次），返回任务 ID。可通过 get_task_status 查询状态。",
        category = ToolHandler.Category.UTILITY,
        location = ToolHandler.Location.LOCAL,
        version = "1.0.0"
)
@SandboxPolicy(allowedPaths = {})
@Component
public class DelayTaskHandler {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final ConcurrentHashMap<String, TaskFuture> tasks = new ConcurrentHashMap<>();

    public String execute(
            @ToolParameter(value = "command", description = "要执行的命令（如 bash 命令或工具调用，目前仅支持 shell 命令）", example = "echo 'hello' > /tmp/out.txt")
            String command,
            @ToolParameter(value = "delay_seconds", description = "延迟秒数", example = "10")
            Integer delaySeconds,
            @ToolParameter(value = "task_id", description = "可选，自定义任务 ID，否则自动生成", required = false)
            String taskId
    ) {
        if (command == null || command.isBlank()) return "错误：缺少 command";
        int delay = (delaySeconds != null && delaySeconds > 0) ? delaySeconds : 0;
        final String id = (taskId != null && !taskId.isBlank()) ? taskId : UUID.randomUUID().toString();

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                ProcessBuilder pb;
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    pb = new ProcessBuilder("cmd", "/c", command);
                } else {
                    pb = new ProcessBuilder("bash", "-c", command);
                }
                Process p = pb.start();
                boolean finished = p.waitFor(30, TimeUnit.SECONDS);
                int exitCode = p.exitValue();
                String result = finished ? ("完成，退出码 " + exitCode) : "超时";
                tasks.put(id, new TaskFuture("COMPLETED", result));
            } catch (Exception e) {
                tasks.put(id, new TaskFuture("FAILED", e.getMessage()));
            }
        }, delay, TimeUnit.SECONDS);

        tasks.put(id, new TaskFuture("SCHEDULED", future));
        return "任务已创建，ID: " + id + "，将在 " + delay + " 秒后执行";
    }

    // 可选：查询任务状态
    public String getStatus(@ToolParameter(value = "task_id") String taskId) {
        TaskFuture tf = tasks.get(taskId);
        if (tf == null) return "任务不存在";
        if ("SCHEDULED".equals(tf.status)) {
            ScheduledFuture<?> future = (ScheduledFuture<?>) tf.data;
            long remaining = future.getDelay(TimeUnit.SECONDS);
            return "状态: 等待执行，剩余 " + remaining + " 秒";
        }
        return "状态: " + tf.status + "，结果: " + tf.data;
    }

    private static class TaskFuture {
        String status;
        Object data;
        TaskFuture(String status, Object data) { this.status = status; this.data = data; }
    }
}