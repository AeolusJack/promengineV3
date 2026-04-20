package com.thirdexploration.promengine.executor.sandbox;

import com.thirdexploration.promengine.executor.tool.registry.ToolDefinition;

import java.nio.file.Path;

/**
 * 沙箱管理器接口，定义了安全执行本地操作的核心方法。
 * 实现类负责隔离文件系统访问、命令执行等高风险操作。
 *
 * 设计要点：
 * - resolve 方法必须确保返回的路径在授权的工作区内。
 * - executeInSandbox 方法用于执行需要沙箱隔离的自定义逻辑（如 Wasm 模块调用）。
 */
public interface SandboxManager {

    /**
     * 将用户提供的相对路径解析为沙箱工作区内的绝对路径，
     * 并校验路径是否在授权范围内。
     *
     * @param relativePath 用户指定的路径（相对于工作区）
     * @return 安全解析后的绝对路径
     * @throws SecurityException 如果检测到路径穿越攻击
     */
    Path resolve(String relativePath) throws SecurityException;

    /**
     * 在沙箱环境中执行指定的工具调用。
     * 适用于 Wasm 模块或需要额外隔离的场景。
     *
     * @param toolName 工具名称（对应 Wasm 模块文件名或预定义操作）
     * @param jsonArgs JSON 格式的参数
     * @return 执行结果字符串
     */
    String executeInSandbox(String toolName, String jsonArgs);

    /**
     * 获取沙箱工作区的根路径。
     */
    Path getWorkspaceRoot();

    Path resolve(String relativePath, ToolDefinition.SandboxPolicyDef policy) throws SecurityException;


    String executeInSandbox(String toolName, String jsonArgs, ToolDefinition.SandboxPolicyDef policy);
}