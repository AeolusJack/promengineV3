package com.thirdexploration.promengine.core;

import com.thirdexploration.promengine.core.domain.SensoryInput;
import com.thirdexploration.promengine.core.domain.TaskContext;
import com.thirdexploration.promengine.core.domain.VitalSigns;

/**
 * 认知生理抽象层，定义了硅基/碳基模式共有的生理行为。
 * 不同模式实现不同的“生命力”特征。
 */
public interface CognitivePhysiology {

    /**
     * 每个 tick 调用（碳基模式 100ms），更新内部生理状态。
     *
     * @param input 感官输入
     * @return 当前生命体征
     */
    VitalSigns tick(SensoryInput input);

    /**
     * 判断是否应该打断当前任务（例如因为疲倦或情绪波动）。
     *
     * @param ctx 当前任务上下文
     * @return true 表示建议打断
     */
    boolean shouldInterrupt(TaskContext ctx);

    /**
     * 获取当前记忆提取的保真度（0~1）。碳基模式随时间衰减。
     *
     * @return 保真度系数
     */
    float getMemoryFidelity();

    /**
     * 获取当前话多话少因子。
     *
     * @return 详细度系数
     */
    float getVerbosityFactor();

    /**
     * 是否处于专注模式。
     *
     * @return true 表示专注中
     */
    boolean isInFocusMode();

    /**
     * 获取当前认知燃料值（仅碳基模式有效，硅基返回常量100）。
     *
     * @return 0-100 的精力值
     */
    int getCurrentFuel();

    /**
     * 增加认知燃料（例如用户主动鼓励）。
     *
     * @param amount 增量
     */
    void boostFuel(int amount);
}