# PromEngine 下一代 Agent 工具系统设计与使用指南

## 1. 概述

PromEngine 工具系统旨在为基于 LLM 的智能体（Agent）提供**安全、可扩展、声明式**的工具调用能力。它借鉴并超越了 OpenClaw 等框架的设计理念，通过**纯注解驱动、位置透明、安全策略即代码**等特性，让开发者可以专注于工具的业务逻辑，而无需关心底层的注册、发现、沙箱隔离和版本管理等复杂性。

本文档适用于以下角色：

- **架构师**：理解系统设计理念与整体结构
- **开发者**：掌握添加新工具、切换运行模式的实用方法
- **运维人员**：了解配置项、日志观测与监控指标
- **扩展贡献者**：理解预留扩展点，为系统增加新沙箱类型或集成外部生态

---

## 2. 架构设计理念

### 2.1 设计目标

| 目标 | 说明 |
|------|------|
| **声明式开发** | 开发者只需在类上添加 `@ToolHandler` 注解，系统自动完成注册、Schema 生成和版本管理 |
| **安全优先** | 通过 `@SandboxPolicy` 声明安全约束，运行时强制校验；支持 Wasm、Docker 等多种隔离级别 |
| **位置透明** | 工具可声明期望的执行位置（本地 / 沙箱 / 远程），系统根据风险等级自动协商或由开发者指定 |
| **版本管理与灰度** | 支持工具多版本并存，可通过配置进行 A/B 测试和流量灰度 |
| **生态开放性** | 预留 MCP 协议、Clawtools 桥接等集成点，可复用 OpenClaw 等外部工具生态 |
| **可观测性** | 内置丰富的日志埋点和指标统计，方便调试与运维 |

### 2.2 核心原则

- **注解驱动 > 手动配置**：用 `@ToolHandler` 代替 XML / 手动注册代码。
- **安全边界前置**：所有文件、网络操作必须经过 `SandboxManager` 校验。
- **职责分离**：`ToolRegistry` 管注册与版本，`SandboxedToolExecutor` 管执行与安全，`ReactOrchestrator` 管 ReAct 循环编排。
- **约定优于配置**：工具类默认扫描 `handlers` 包，`execute` 方法名固定，参数注解自动映射。

---

## 3. 核心组件说明

### 3.1 注解体系

#### `@ToolHandler`
标记一个类为工具处理器。Spring 启动时自动扫描并注册。

```java
@ToolHandler(
    name = "calculator",                     // 工具名（全局唯一）
    description = "执行简单的数学运算",        // 提供给 LLM 的描述
    category = Category.UTILITY,             // 分类，用于 UI 分组
    location = Location.LOCAL,               // 期望执行位置
    version = "1.0.0"                        // 版本号
)
public class CalculatorHandler { ... }
```

#### `@ToolParameter`
标记 `execute` 方法的参数，用于自动生成 JSON Schema 和参数校验。

```java
public String execute(
    @ToolParameter(value = "expression", description = "数学表达式", example = "2+3")
    String expression,
    @ToolParameter(value = "precision", required = false)
    Integer precision
) { ... }
```

#### `@SandboxPolicy`
声明工具的安全约束，由 `SandboxManager` 强制执行。

```java
@SandboxPolicy(
    allowedPaths = {"documents", "projects"},   // 允许访问的子目录
    allowNetwork = false,                       // 是否允许网络访问
    maxMemoryMB = 64,
    maxExecutionSeconds = 30,
    requireConfirmation = false                 // 高危操作是否需要用户确认
)
public class WriteFileHandler { ... }
```

### 3.2 注册与发现 (`ToolRegistry`)

- 以 **工具名** 为一级键，内部维护 **版本 → 工具实例** 的映射。
- 支持 `resolve(toolName, requestedVersion)` 解析当前应使用的版本（考虑灰度配置）。
- 提供 `getAllToolCallbacks()` 生成 Spring AI 兼容的 `ToolCallback` 列表。

### 3.3 自动扫描注册 (`ToolAutoRegistrar`)

- 实现 `ApplicationContext.getBeansWithAnnotation(ToolHandler.class)`。
- 遍历所有 `@ToolHandler` Bean，反射获取 `execute` 方法，提取参数元数据。
- 构建 `ToolDefinition` 和 `ToolInvoker`，调用 `ToolRegistry.register()`。

### 3.4 工具执行器 (`SandboxedToolExecutor`)

- 实现 `ToolExecutor` 接口。
- `execute(ToolCall)` 从 `ToolRegistry` 解析工具，调用其 `ToolInvoker`。
- 执行前检查 `@SandboxPolicy`，通过 `SandboxManager` 校验路径等约束。

### 3.5 沙箱管理器 (`SandboxManager`)

接口定义：

```java
public interface SandboxManager {
    Path resolve(String relativePath, ToolDefinition.SandboxPolicyDef policy);
    String executeInSandbox(String toolName, String jsonArgs, ToolDefinition.SandboxPolicyDef policy);
    Path getWorkspaceRoot();
}
```

当前实现：

- `WasmSandboxManager`：基于 Chicory 1.5.3 加载 `.wasm` 模块执行，实现强隔离。
- 可扩展 `DockerSandboxManager`、`NoOpSandboxManager`（仅路径校验）。

### 3.6 ReAct 编排器 (`ReactOrchestrator`)

- 注入 `ChatClient.Builder`、`ToolExecutor`、`MemoryService`、`PromptManager`。
- 循环执行：
    1. 检索记忆，构建系统提示词（含工具描述）。
    2. 调用 LLM（携带 `ToolCallback` 列表）。
    3. 若 LLM 返回工具调用，执行工具并将结果作为 `ToolResponseMessage` 追加到对话历史。
    4. 重复直至无工具调用或达到最大步数。
- 最终回复存储到记忆系统。

### 3.7 简单编排器 (`SimpleOrchestrator`)

- 单轮对话模式，不涉及工具调用。
- 通过 `promengine.orchestrator.mode=SIMPLE` 激活（默认）。

---

## 4. 开发指南

### 4.1 添加一个新工具

1. 在 `com.thirdexploration.promengine.executor.tool.handlers` 包下创建 Java 类。
2. 添加 `@ToolHandler` 注解，填写名称、描述等信息。
3. 定义 `public String execute(...)` 方法，并用 `@ToolParameter` 标注参数。
4. 如需安全约束，添加 `@SandboxPolicy` 注解。
5. 重新启动应用，观察日志确认工具已自动注册。

**示例：计算器工具**

```java
@ToolHandler(name = "calculator", description = "执行加减乘除运算", category = Category.UTILITY)
public class CalculatorHandler {
    public String execute(
        @ToolParameter(value = "expression", description = "数学表达式") String expression) {
        // 实现计算逻辑
        return String.valueOf(eval(expression));
    }
}
```

### 4.2 切换运行模式

在 `application.yml` 中配置：

```yaml
promengine:
  orchestrator:
    mode: REACT   # 或 SIMPLE
    max-steps: 8
    tool-use-enabled: true
```

- `SIMPLE`：仅对话，无工具调用。
- `REACT`：启用 ReAct 循环，LLM 可调用已注册工具。

### 4.3 控制日志详细程度

```yaml
promengine:
  orchestrator:
    verbose-logging: true   # 开启后输出完整的 ReAct 步骤、Prompt 内容等
logging:
  level:
    com.thirdexploration.promengine.executor: DEBUG
    com.thirdexploration.promengine.executor.tool: TRACE
```

### 4.4 扩展新的沙箱类型

1. 实现 `SandboxManager` 接口。
2. 添加 `@Component` 并使用 `@ConditionalOnProperty` 指定激活条件。
3. 在配置中切换 `promengine.orchestrator.sandbox.type`。

---

## 5. 运维与观测

### 5.1 关键日志输出

开启 `verbose-logging: true` 后，每次请求可观测到：

- 可用工具列表
- 每轮 ReAct 的对话历史（含工具调用）
- LLM 返回的工具调用指令
- 工具执行的参数、耗时和结果
- 最终回复和总步数

### 5.2 配置项参考

```yaml
promengine:
  orchestrator:
    mode: REACT
    max-steps: 8
    tool-use-enabled: true
    verbose-logging: true
    sandbox:
      enabled: true
      type: WASM                      # WASM, DOCKER, DISABLED
      wasm-modules-path: ./wasm-modules/
      workspace-path: ./sandbox-workspace/
```

### 5.3 监控指标（预留）

`ToolRegistry` 中的 `ToolStats` 已记录：
- 各工具总调用次数
- 各版本调用次数
- 成功率（需在执行器中补充）

可接入 Micrometer 导出到 Prometheus。

---

## 6. Demo 示例与测试流程

### 6.1 环境准备

- 启动 Ollama 服务，拉取模型（如 `gemma4-custom:q4`、`nomic-embed-text`）。
- 启动 ChromaDB 向量数据库（用于记忆检索）。
- 确保 `application.yml` 配置正确。

### 6.2 创建并测试计算器工具

1. 按 4.1 节创建 `CalculatorHandler`。
2. 重启应用，日志应显示：
   ```
   Auto-registered tool: calculator v1.0.0 (category: UTILITY, location: LOCAL)
   ```
3. 发送测试请求：
   ```bash
   curl -X POST http://localhost:8080/api/v1/chat \
     -H "Content-Type: application/json" \
     -d '{"sessionId":"test","message":"请计算15乘以7"}'
   ```
4. 观察日志，应包含 ReAct 循环、工具调用和最终答案 `105`。
5. 响应 JSON 应包含正确答案。

### 6.3 测试文件操作工具

- 使用 `write_file` 和 `read_file` 工具，验证沙箱路径限制是否生效。
- 尝试路径穿越（如 `../outside.txt`），应被 `SandboxManager.resolve()` 拒绝。

---

## 7. 预留扩展入口

### 7.1 MCP 协议集成

`promengine-ecosystem` 模块中的 `MCPClientAdapter` 预留了接口，可连接任意 MCP 服务器，自动发现并注册工具。后续只需实现 `discoverTools()` 方法即可。

### 7.2 Clawtools 桥接

可通过 GraalJS 运行 `clawtools`，将其工具转换为 PromEngine 的 `ToolDefinition`。预留 `ClawtoolsAdapter` 类，待后续完善。

### 7.3 宏工具（Macro Tools）

`ToolDefinition` 中已预留 `macroSteps` 字段，未来可支持 YAML 定义组合工具，在 `ToolRegistry` 中动态生成复合 `ToolInvoker`。

### 7.4 工具市场与可视化

`ToolRegistry` 提供的 `getAllDefinitions()` 方法可用于璇玑台 UI 渲染工具列表，后续可增加启用/禁用、灰度配置等管理接口。

---

## 8. 常见问题排查

| 现象 | 可能原因 | 排查方法 |
|------|----------|----------|
| 工具未注册 | 类未在 Spring 扫描路径内 / 缺少 `@ToolHandler` | 检查日志是否有 `Auto-registered tool` 输出 |
| LLM 不调用工具 | 提示词未正确包含工具描述 / 模型不支持 Function Calling | 开启 verbose 日志，查看实际 Prompt 和 LLM 响应 |
| 工具执行报路径错误 | 未通过 `SandboxManager.resolve()` 校验 | 确保文件路径操作使用 `sandboxManager.resolve()` |
| ReAct 循环提前终止 | `max-steps` 太小 / 工具返回错误导致 LLM 无法继续 | 检查日志，适当增大 `max-steps` |

---

## 9. 总结

PromEngine 下一代工具系统通过**注解驱动、安全前置、版本可管理、生态开放**的设计，为 Java 技术栈的 AI Agent 开发提供了坚实的基础。开发者可以像编写普通业务代码一样快速扩展 Agent 的能力，同时获得企业级的安全保障和可观测性。随着 MCP、Clawtools 等生态的进一步集成，PromEngine 将成为连接本地大模型与丰富数字能力的桥梁。