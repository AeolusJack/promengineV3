package com.thirdexploration.promengine.memory.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 记忆来源追踪信息，用于审计和可信度评估。
 * 记录记忆的创建者、来源类型、验证状态等元数据。
 */
@Data
@Builder
public class Provenance {

    /**
     * 创建者标识（user_id 或 agent_id）
     */
    private String createdBy;

    /**
     * 来源类型：user_input, agent_generated, tool_output, distilled, imported
     */
    private String source;

    /**
     * 验证状态：unverified, verified, rejected
     */
    private String verificationStatus;

    /**
     * 验证时间
     */
    private Instant verifiedAt;

    /**
     * 验证者标识（user_id 或 agent_id）
     */
    private String verifiedBy;

    /**
     * 原始来源引用（如原始记忆ID，用于蒸馏追溯）
     */
    private String derivedFrom;

    /**
     * 判断是否已验证
     */
    public boolean isVerified() {
        return "verified".equals(verificationStatus);
    }

    /**
     * 判断是否被拒绝
     */
    public boolean isRejected() {
        return "rejected".equals(verificationStatus);
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建用户输入的来源信息
     * @param userId 用户标识
     * @return Provenance 实例
     */
    public static Provenance userInput(String userId) {
        return Provenance.builder()
                .createdBy(userId)
                .source("user_input")
                .verificationStatus("unverified")
                .build();
    }

    /**
     * 创建 Agent 生成的来源信息
     * @param agentId Agent 标识
     * @return Provenance 实例
     */
    public static Provenance agentGenerated(String agentId) {
        return Provenance.builder()
                .createdBy(agentId)
                .source("agent_generated")
                .verificationStatus("unverified")
                .build();
    }

    /**
     * 创建工具输出的来源信息
     * @param toolName 工具名称
     * @return Provenance 实例
     */
    public static Provenance toolOutput(String toolName) {
        return Provenance.builder()
                .createdBy("tool:" + toolName)
                .source("tool_output")
                .verificationStatus("unverified")
                .build();
    }

    /**
     * 创建知识蒸馏的来源信息（从多条记忆提炼而来）
     * @param agentId 执行蒸馏的 Agent 标识
     * @param sourceMemoryIds 源记忆ID列表
     * @return Provenance 实例
     */
    public static Provenance distilled(String agentId, java.util.List<String> sourceMemoryIds) {
        return Provenance.builder()
                .createdBy(agentId)
                .source("distilled")
                .verificationStatus("unverified")
                .derivedFrom(String.join(",", sourceMemoryIds))
                .build();
    }

    /**
     * 创建外部导入的来源信息
     * @param importerId 导入者标识
     * @param externalSource 外部来源描述
     * @return Provenance 实例
     */
    public static Provenance imported(String importerId, String externalSource) {
        return Provenance.builder()
                .createdBy(importerId)
                .source("imported")
                .verificationStatus("unverified")
                .derivedFrom(externalSource)
                .build();
    }

    // ==================== 状态变更方法 ====================

    /**
     * 标记为已验证
     * @param verifierId 验证者标识
     * @return 当前实例（支持链式调用）
     */
    public Provenance markVerified(String verifierId) {
        this.verificationStatus = "verified";
        this.verifiedAt = Instant.now();
        this.verifiedBy = verifierId;
        return this;
    }

    /**
     * 标记为已拒绝
     * @param verifierId 验证者标识
     * @return 当前实例（支持链式调用）
     */
    public Provenance markRejected(String verifierId) {
        this.verificationStatus = "rejected";
        this.verifiedAt = Instant.now();
        this.verifiedBy = verifierId;
        return this;
    }

    /**
     * 重置为未验证状态
     * @return 当前实例
     */
    public Provenance resetVerification() {
        this.verificationStatus = "unverified";
        this.verifiedAt = null;
        this.verifiedBy = null;
        return this;
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取来源类型的可读描述
     */
    public String getSourceDescription() {
        return switch (source) {
            case "user_input" -> "用户输入";
            case "agent_generated" -> "Agent 生成";
            case "tool_output" -> "工具输出";
            case "distilled" -> "知识蒸馏";
            case "imported" -> "外部导入";
            default -> source;
        };
    }

    /**
     * 获取验证状态的可读描述
     */
    public String getVerificationDescription() {
        return switch (verificationStatus) {
            case "verified" -> "已验证";
            case "rejected" -> "已拒绝";
            default -> "未验证";
        };
    }

    /**
     * 创建当前实例的深拷贝
     */
    public Provenance copy() {
        return Provenance.builder()
                .createdBy(this.createdBy)
                .source(this.source)
                .verificationStatus(this.verificationStatus)
                .verifiedAt(this.verifiedAt)
                .verifiedBy(this.verifiedBy)
                .derivedFrom(this.derivedFrom)
                .build();
    }
}