package com.thirdexploration.promengine.executor.execution;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务队列管理器，负责任务的异步执行、超时控制和资源清理。
 * <p>
 * 使用虚拟线程（Virtual Threads）实现高并发轻量级任务处理，每个任务独立运行，
 * 支持超时取消和状态追踪。
 *
 * @author Third Exploration
 * @version 1.0
 */
@Slf4j
@Component
public class TaskQueue {

    /** 任务执行线程池（使用虚拟线程） */
    private ExecutorService executor;

    /** 用于跟踪正在执行的任务 Future，便于取消和状态查询 */
    private final Map<String, CompletableFuture<?>> runningTasks = new ConcurrentHashMap<>();

    /** 队列是否已启动 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 默认任务超时时间（秒） */
    private static final long DEFAULT_TASK_TIMEOUT_SECONDS = 60L;

    /**
     * 初始化任务队列，创建虚拟线程执行器。
     */
    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) {
            // 使用虚拟线程执行器，每个任务一个虚拟线程，资源占用极低
            executor = Executors.newVirtualThreadPerTaskExecutor();
            log.info("TaskQueue started with virtual thread executor");
        }
    }

    /**
     * 关闭任务队列，等待所有运行中的任务完成或超时。
     */
    @PreDestroy
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            log.info("Shutting down TaskQueue, waiting for {} running tasks", runningTasks.size());
            // 尝试取消所有运行中的任务
            runningTasks.values().forEach(future -> future.cancel(true));
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    log.warn("TaskQueue did not terminate gracefully, forcing shutdown");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.warn("TaskQueue shutdown interrupted");
            }
            runningTasks.clear();
            log.info("TaskQueue shutdown completed");
        }
    }

    /**
     * 提交一个任务到队列中异步执行。
     *
     * @param taskId   任务唯一标识
     * @param task     待执行的任务（Runnable）
     * @param timeout  超时时间（秒），如果为 null 则使用默认超时
     * @param callback 任务完成后的回调（无论成功或失败）
     * @return CompletableFuture 代表异步执行的结果
     */
    public CompletableFuture<Void> submit(String taskId, Runnable task, Long timeout, TaskCallback callback) {
        if (!running.get()) {
            throw new IllegalStateException("TaskQueue is not running");
        }

        long effectiveTimeout = timeout != null ? timeout : DEFAULT_TASK_TIMEOUT_SECONDS;

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            log.debug("Task {} started execution", taskId);
            task.run();
            log.debug("Task {} completed execution", taskId);
        }, executor).orTimeout(effectiveTimeout, TimeUnit.SECONDS);

        // 注册任务
        runningTasks.put(taskId, future);

        // 处理完成或异常后的清理和回调
        future.whenComplete((result, throwable) -> {
            runningTasks.remove(taskId);
            if (callback != null) {
                try {
                    if (throwable != null) {
                        callback.onFailure(taskId, throwable);
                    } else {
                        callback.onSuccess(taskId);
                    }
                } catch (Exception e) {
                    log.error("Task callback threw exception for task {}", taskId, e);
                }
            }
            if (throwable != null) {
                if (throwable instanceof TimeoutException) {
                    log.warn("Task {} timed out after {} seconds", taskId, effectiveTimeout);
                } else {
                    log.error("Task {} failed with exception", taskId, throwable);
                }
            }
        });

        return future;
    }

    /**
     * 提交一个带有返回值的任务。
     *
     * @param taskId   任务唯一标识
     * @param task     待执行的任务（Supplier）
     * @param timeout  超时时间（秒）
     * @param callback 任务完成后的回调
     * @param <T>      返回值类型
     * @return CompletableFuture 包含任务执行结果
     */
    public <T> CompletableFuture<T> submit(String taskId, java.util.function.Supplier<T> task, Long timeout, TaskResultCallback<T> callback) {
        if (!running.get()) {
            throw new IllegalStateException("TaskQueue is not running");
        }

        long effectiveTimeout = timeout != null ? timeout : DEFAULT_TASK_TIMEOUT_SECONDS;

        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            log.debug("Task {} started execution (with result)", taskId);
            T result = task.get();
            log.debug("Task {} completed execution with result", taskId);
            return result;
        }, executor).orTimeout(effectiveTimeout, TimeUnit.SECONDS);

        runningTasks.put(taskId, future);

        future.whenComplete((result, throwable) -> {
            runningTasks.remove(taskId);
            if (callback != null) {
                try {
                    if (throwable != null) {
                        callback.onFailure(taskId, throwable);
                    } else {
                        callback.onSuccess(taskId, result);
                    }
                } catch (Exception e) {
                    log.error("Task callback threw exception for task {}", taskId, e);
                }
            }
            if (throwable != null) {
                if (throwable instanceof TimeoutException) {
                    log.warn("Task {} timed out after {} seconds", taskId, effectiveTimeout);
                } else {
                    log.error("Task {} failed with exception", taskId, throwable);
                }
            }
        });

        return future;
    }

    /**
     * 取消指定任务。
     *
     * @param taskId 任务ID
     * @return true 如果任务被成功取消
     */
    public boolean cancel(String taskId) {
        CompletableFuture<?> future = runningTasks.get(taskId);
        if (future != null) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                log.info("Task {} cancelled", taskId);
                runningTasks.remove(taskId);
            }
            return cancelled;
        }
        return false;
    }

    /**
     * 获取当前正在运行的任务数量。
     *
     * @return 运行中任务数
     */
    public int getActiveTaskCount() {
        return runningTasks.size();
    }

    /**
     * 检查任务是否正在运行。
     *
     * @param taskId 任务ID
     * @return true 如果任务存在且未完成
     */
    public boolean isRunning(String taskId) {
        CompletableFuture<?> future = runningTasks.get(taskId);
        return future != null && !future.isDone();
    }

    /**
     * 获取所有运行中任务的ID列表。
     *
     * @return 任务ID集合
     */
    public java.util.Set<String> getRunningTaskIds() {
        return Map.copyOf(runningTasks).keySet();
    }

    /**
     * 清理已完成或已取消任务的 Future 引用（通常由 whenComplete 自动完成，
     * 此方法用于手动清理可能残留的引用）。
     */
    public void purgeCompletedTasks() {
        runningTasks.entrySet().removeIf(entry -> entry.getValue().isDone());
    }

    // ---------- 回调接口定义 ----------

    /**
     * 无返回值任务的回调接口。
     */
    public interface TaskCallback {
        void onSuccess(String taskId);
        void onFailure(String taskId, Throwable throwable);
    }

    /**
     * 有返回值任务的回调接口。
     *
     * @param <T> 返回值类型
     */
    public interface TaskResultCallback<T> {
        void onSuccess(String taskId, T result);
        void onFailure(String taskId, Throwable throwable);
    }
}