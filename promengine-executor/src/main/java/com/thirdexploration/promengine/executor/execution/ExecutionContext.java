package com.thirdexploration.promengine.executor.execution;

import com.thirdexploration.promengine.core.domain.TaskContext;
import com.thirdexploration.promengine.core.domain.UserInput;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 执行上下文，封装单次任务执行所需的所有状态信息。
 * <p>
 * 该上下文贯穿整个执行链路，包括记忆检索、提示词构建、模型调用、工具执行等环节。
 * 同时支持在 MicroAgent 之间传递共享状态（黑板模式）。
 *
 * @author Third Exploration
 * @version 1.0
 */
@Data
@Builder
public class ExecutionContext {

    /** 执行唯一标识 */
    private final String executionId;

    /** 会话标识，用于关联多轮对话 */
    private final String sessionId;

    /** 用户标识 */
    private final String userId;



    /** 用户原始输入 */
    private final UserInput userInput;

    /** 任务类型，用于选择提示词模板等 */
    private String taskType;

    /** 执行开始时间戳 */
    private final Instant startTime;

    /** 共享变量黑板，用于在多个 MicroAgent 或执行步骤间传递数据 */
    @Builder.Default
    private final Map<String, Object> attributes = new HashMap<>();

    /** 当前执行状态 */
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.CREATED;

    /** 执行耗时（毫秒），在完成时填充 */
    private Long elapsedMs;

    /** 执行过程中发生的错误信息 */
    private String errorMessage;

//    private String taskContext;

    /**
     * 便捷构造方法：基于 UserInput 创建默认上下文。
     *
     * @param input 用户输入
     * @return 初始化的 ExecutionContext
     */
    public static ExecutionContext of(UserInput input) {
        ExecutionContext ctx = ExecutionContext.builder()
                .executionId(generateExecutionId())
                .sessionId(input.getSessionId())
                .userId(input.getUserId())
                .userInput(input)
                .startTime(Instant.now())
                .status(ExecutionStatus.CREATED)
                .build();
        // 合并 metadata 到 attributes
        if (input.getMetadata() != null) {
            ctx.getAttributes().putAll(input.getMetadata());
        }
        return ctx;
    }

    /**
     * 基于 TaskContext（来自上层）创建执行上下文。
     *
     * @param taskContext 任务上下文
     * @return ExecutionContext
     */
    public static ExecutionContext from(TaskContext taskContext) {
        ExecutionContextBuilder builder = ExecutionContext.builder()
                .executionId(generateExecutionId())
                .sessionId(taskContext.getUserInput().getSessionId())
                .userId(taskContext.getUserId())
                .userInput(taskContext.getUserInput())
                .taskType(taskContext.getTaskType())
                .startTime(Instant.now());

        // 复制 TaskContext 中的变量到 attributes
        if (taskContext.getVariables() != null) {
            builder.attributes(new HashMap<>(taskContext.getVariables()));
        }

        return builder.build();
    }

    /**
     * 转换为核心域的 TaskContext，用于与记忆、提示词等模块交互。
     *
     * @return TaskContext
     */
    public TaskContext toTaskContext() {
        return TaskContext.builder()
                .taskType(this.taskType)
                .userId(this.userId)
                .userInput(this.userInput)
                .variables(new HashMap<>(this.attributes))
                .build();
    }

    /**
     * 向黑板中放入一个键值对。
     *
     * @param key   键
     * @param value 值
     * @return this（支持链式调用）
     */
    public ExecutionContext putAttribute(String key, Object value) {
        this.attributes.put(key, value);
        return this;
    }

    /**
     * 从黑板中获取值，并进行类型转换。
     *
     * @param key  键
     * @param type 期望的类型
     * @param <T>  类型参数
     * @return 值，如果不存在或类型不匹配则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 标记执行开始。
     */
    public void markRunning() {
        this.status = ExecutionStatus.RUNNING;
    }

    /**
     * 标记执行成功完成。
     *
     * @param elapsedMs 耗时毫秒数
     */
    public void markCompleted(long elapsedMs) {
        this.status = ExecutionStatus.COMPLETED;
        this.elapsedMs = elapsedMs;
    }

    /**
     * 标记执行失败。
     *
     * @param errorMessage 错误信息
     */
    public void markFailed(String errorMessage) {
        this.status = ExecutionStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * 标记执行被取消。
     */
    public void markCancelled() {
        this.status = ExecutionStatus.CANCELLED;
    }

    /**
     * 判断执行是否已完成（包括成功、失败、取消）。
     *
     * @return true 表示已结束
     */
    public boolean isFinished() {
        return status == ExecutionStatus.COMPLETED
                || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.CANCELLED;
    }

    // ---------- 私有辅助方法 ----------

    private static String generateExecutionId() {
        return "exec_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String extractUserId(UserInput userInput) {
        // 简化实现，实际可从 UserInput 的元数据或 SecurityContext 获取
        //return "default-user";
         return userInput.getUserId();
    }

    /**
     * 执行状态枚举。
     */
    public enum ExecutionStatus {
        CREATED,    // 已创建，尚未开始
        RUNNING,    // 正在执行
        COMPLETED,  // 成功完成
        FAILED,     // 执行失败
        CANCELLED   // 被取消
    }
}