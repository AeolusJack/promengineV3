# PromEngineV3 后端代码文档

## 项目简介

PromEngineV3 是一个轻量级、可嵌入、自我进化的智能体运行时框架。它以“轻核心、插件化、本地优先、模型无关”为核心理念，提供从记忆存储、多 Agent 协作、工具调用、安全管控、人格内核到自驱调度的一站式解决方案。

本项目为 PromEngine 的后端实现，基于 **Spring Boot 3.x** 和 **Java 21**（充分利用虚拟线程等新特性），采用多模块 Maven 结构，支持与 **Ollama** 等本地大模型无缝集成。项目内置了广泛的**生态适配器**，可原生接入 **OpenClaw Skills 技能生态**、**MCP 协议工具链**、飞书/微信等 IM 平台以及浏览器自动化等能力，并支持通过插件化机制持续扩展。

---

## 当前项目进展与展望

### 已实现的核心能力 (v6.3 当前状态)

| 领域           | 完成情况 | 说明                                                         |
| :------------- | :------- | :----------------------------------------------------------- |
| **核心运行时** | ✅ 稳定  | `AgentRuntime` 完整生命周期管理，支持硅基/碳基双模式。        |
| **模型网关**   | ✅ 稳定  | 原生 Ollama 适配器（支持流式/非流式）、智能语义路由、熔断降级。 |
| **分层记忆**   | ✅ 稳定  | 热存储 (SQLite) + 温存储 (Parquet + JSONL 摘要) + 冷存储 (压缩归档)。 |
| **向量检索**   | ✅ 可用  | LanceDB / ChromaDB 双后端支持，已打通嵌入模型。                |
| **流式对话**   | ✅ 稳定  | 提供 SSE 标准流与纯文本调试流双接口。                          |
| **提示词管理** | ✅ 可用  | Jinja2 模板渲染、上下文构建、智能压缩。                        |
| **配置中心**   | ✅ 可用  | 前端可修改配置项，支持版本回滚、审批流程。                      |
| **生态适配器** | ⚙️ 持续集成 | 已实现 MCP、OpenClaw、飞书、微信、浏览器自动化等适配器骨架。    |
| **Swarm 集群** | 🚧 实验性 | 微智能体并行调度框架已就绪，待大规模测试。                     |
| **遗憾引擎**   | 🚧 实验性 | 对比式遗憾挖掘已实现，尚未与人格微调完全联动。                 |
| **前端界面**   | 📅 规划中 | 计划提供 React 参考实现（璇玑台），当前可通过 Swagger UI 调试。 |

### 近期路线图 (v6.3 → v7.0)

- **Q2 2026**：完善生态适配器文档与示例，发布 MCP/OpenClaw 集成最佳实践。
- **Q3 2026**：推出“璇玑台”前端 MVP（基于 Open WebUI 二次开发），实现思维涟漪可视化。
- **Q4 2026**：Swarm 集群正式发布，支持 100+ 微 Agent 并发协作。
- **Q1 2027**：遗憾引擎与人格参数闭环，实现真正的“使用中进化”。
- **远期展望**：支持去中心化记忆存储（IPFS / Solid Pod），实现跨设备记忆主权。

---

## 技术栈

| 组件               | 当前采用技术                                  | 备选/可替换方案                                    | 考量说明                                                     |
| :----------------- |:----------------------------------------| :------------------------------------------------- | :----------------------------------------------------------- |
| **主框架**         | Spring Boot 3.2.5 + Java 21 (虚拟线程)      | Quarkus、Micronaut                                 | Spring 生态成熟，虚拟线程大幅简化并发模型。                   |
| **模型调用**       | 原生 OkHttp + Ollama API                  | Spring AI (Ollama 适配器)、OpenAI 官方 SDK         | 原生实现保持轻量与可控，同时预留 Spring AI 接口以备无缝切换。 |
| **向量存储**       |  ChromaDB (docker) / LanceDB (JNI)      | Qdrant、Weaviate、Milvus                           | LanceDB 零配置、高性能；ChromaDB 作为备选保证兼容性。         |
| **热存储**         | SQLite + HikariCP                       | H2、PostgreSQL                                     | SQLite 本地优先、零运维，适合个人数据管家场景。               |
| **温/冷存储**      | Apache Parquet + Arrow + JSONL 摘要       | Delta Lake、Iceberg                                | Parquet 列式压缩比高，适合海量归档；JSONL 摘要加速温数据查询。 |
| **关键词索引**     | Apache Lucene                           | Elasticsearch                                      | Lucene 嵌入式、轻量，避免引入额外服务依赖。                   |
| **提示词渲染**     | Jinjava (Jinja2 风格)                     | StringTemplate、Thymeleaf                          | Jinja2 语法在 AI 领域使用广泛，模板复用性强。                 |
| **配置管理**       | Spring Boot Configuration Properties    | Nacos、Consul                                      | 本地配置优先，未来可扩展至远程配置中心。                       |
| **可观测性**       | Micrometer + Prometheus + Langfuse (可选) | OpenTelemetry                                      | 与 Spring Boot Actuator 无缝集成，Langfuse 提供 LLM 专用追踪。 |
| **构建工具**       | Maven                                   | Gradle                                             | Maven 在 Java 生态普及度更高，易于上手。                      |

---

## 模块架构说明

PromEngine 采用 Maven 多模块结构，各模块职责清晰，依赖关系单向（上层依赖下层）。

```
promengine/
├── pom.xml (父POM，统一版本管理)
├── promengine-core                      # 核心接口、领域模型、异常定义
├── promengine-memory                    # 分层存储、向量检索、记忆管理
├── promengine-model                     # 模型网关、多适配器、智能路由
├── promengine-executor                  # 运行时实现、编排器、工具注册
├── promengine-skill                     # 技能系统、动态加载
├── promengine-evolution                 # 遗憾引擎、自我进化
├── promengine-cognition                 # 认知生理层（硅基/碳基双模式）
├── promengine-swarm                     # 微智能体集群调度
├── promengine-neuro                     # 元认知、思维涟漪
├── promengine-temporal                  # 主观时间感知
├── promengine-verifier                  # 形式化验证、意图过滤
├── promengine-ethics                    # 伦理治理、审计
├── promengine-prompt                    # 提示词管理、模板渲染
├── promengine-apex                      # API 成本管控与配额
├── promengine-ecosystem/                # 生态适配器聚合模块
│   ├── promengine-adapter-litellm       # LiteLLM 适配
│   ├── promengine-adapter-mcp           # MCP 协议适配
│   ├── promengine-adapter-openclaw      # OpenClaw 技能适配
│   ├── promengine-adapter-browseruse    # 浏览器自动化
│   ├── promengine-adapter-feishu        # 飞书适配
│   ├── promengine-adapter-weixin        # 微信适配
│   └── promengine-adapter-wps           # WPS 适配
├── promengine-identity-proxy            # 数字身份代理
├── promengine-psych-aid                 # 心理急救模块
├── promengine-devtools                  # 开发者工具（调试、影子运行）
├── promengine-runtime                   # 运行时组装（启动入口）
├── promengine-web                       # Web 层（REST API、WebSocket）
└── promengine-spring-boot-starter       # Spring Boot Starter（自动配置）
```

### 核心模块简介

| 模块                  | 职责                                                                      |
| --------------------- | ------------------------------------------------------------------------- |
| **promengine-core**   | 定义框架所有核心接口（`AgentRuntime`、`MemoryService`、`ModelGateway`等）、领域对象、基础异常和工具类。其他所有模块均依赖此模块。 |
| **promengine-memory** | 实现分层存储（热/温/冷）、向量检索、Lucene 索引、记忆迁移、归并压缩、冷启动预热。                                   |
| **promengine-model**  | 模型网关实现，支持多提供者（Ollama、LiteLLM、OpenRouter），包含智能语义路由、负载感知、熔断降级。                   |
| **promengine-executor** | 运行时核心实现，包含 `AgentRuntimeImpl`、`Orchestrator`、工具注册表、任务队列。                        |
| **promengine-skill**  | 技能系统，支持从 JAR 或 YAML 动态加载技能，提供热卸载能力。                                           |
| **promengine-cognition** | 硅基/碳基双模式认知生理层，包含认知燃料管理、防御机制、生命体征监控。                                         |
| **promengine-prompt** | 提示词模板管理、上下文构建、Jinja2 渲染、智能压缩。                                                   |
| **promengine-apex**   | API 成本中心：配额管理、实时成本追踪、熔断器、审计日志。                                                  |
| **promengine-web**    | REST API 控制器（聊天、配置、系统状态），WebSocket 支持（思维涟漪）。                                     |

---

## 生态适配器详细说明

PromEngine 的核心设计哲学是“核心自主，边缘复用”。我们将生态适配能力集中放置在 `promengine-ecosystem` 聚合模块下，使框架能够无缝接入业界成熟的工具链与能力市场。

| 适配器 | 复用的生态资产 | 核心价值 |
| :--- | :--- | :--- |
| **OpenClaw 适配器** | OpenClaw 技能生态（Skill.md）及 ClawHub 技能市场 | 原生兼容 OpenClaw 的 SKILL.md 技能格式，可复用海量社区技能，极大扩展 Agent 的执行能力。 |
| **MCP 适配器** | Anthropic MCP (Model Context Protocol) 工具生态 | 遵循标准 MCP 协议，能发现并调用任何兼容 MCP 的远程工具服务器，将工具集成成本降至最低。 |
| **LiteLLM 适配器** | LiteLLM 模型网关 | 将 100+ 种不同模型提供商的 API 统一为 OpenAI 兼容格式，并内置成本追踪。 |
| **浏览器自动化适配器** | browser-use 等开源项目 | 将 AI 操控浏览器的能力封装为标准工具，让 Agent 具备与网页交互的“手”。 |
| **飞书/微信/WPS 适配器** | 各平台官方 API 或 SDK | 让 Agent 能够作为“数字分身”，在飞书、微信、WPS 等应用中收发消息、处理文档。 |
| **Open WebUI 适配器** | Open WebUI 前端界面 | 可作为 OpenAI 兼容的后端服务，直接接入 Open WebUI，获得功能完备的 Web 聊天界面。 |

通过上述适配器，PromEngine 不仅能调用大模型，更能真正成为连接现实世界各种服务和工具的“数字伙伴”。

---

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- Ollama（可选，用于本地模型）
- Docker（可选，用于 ChromaDB 等外部存储）

### 1. 克隆并构建项目

```bash
git clone https://github.com/your-org/promengineV3.git
cd promengine
mvn clean install -DskipTests
```

### 2. 启动 Ollama（如使用本地模型）

```bash
ollama serve
ollama pull gemma4-custom:q4   # 或其他模型
```

### 3. 配置应用

编辑 `promengine-runtime/src/main/resources/application.yml`：

```yaml
promengine:
  models:
    providers:
      - id: ollama
        type: ollama
        endpoint: http://localhost:11434
        models:
          - name: gemma4-custom:q4
            cost-per-1k-tokens: 0.0
          - name: qwen2.5:14b
            cost-per-1k-tokens: 0.0
  cognition:
    mode: silicon   # 或 carbon
```

### 4. 启动应用

```bash
cd promengine-runtime
mvn spring-boot:run
```

启动成功后会显示彩色 ASCII 横幅。

### 5. 测试对话

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test-001","message":"你好，请介绍一下你自己"}'
```

---

## 适用场景与目标用户

PromEngine 设计为面向多种场景和用户的通用智能体框架：

- **个人用户**：作为长期数字伙伴，辅助日程管理、知识管理、情感陪伴、创意写作。**本地优先**的特性确保了个人数据的隐私与安全。
- **开发者**：作为可嵌入的智能体运行时，快速构建 AI 原生应用。丰富的生态适配器让开发者可以专注于业务逻辑，而无需重复“造轮子”。
- **企业用户**：适用于需要私有化部署、数据不出域、严格审计的合规场景，如内部客服、流程自动化、长文档处理等。

---

## 主流框架横向对比

为了帮助您更好地理解 PromEngine 的定位，这里将其与业界主流的 Agent 框架进行横向对比。

| 维度 | **PromEngine** | LangChain / LangGraph | AutoGen | CrewAI | OpenClaw |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **核心定位** | 本地优先、自我进化的“数字伙伴”运行时 | 综合性 LLM 应用编排框架，生态丰富 | 微软开源的多 Agent 协作框架，强调自主对话 | 轻量级多 Agent 协作框架，强调角色分工 | 强调结构化执行与工作流可靠性，专注任务自动化 |
| **架构风格** | 认知生理抽象（硅/碳基）、双核并行（Anima/Cortex） | 基于图（Graph）或链（Chain）的有向无环图（DAG）编排 | 基于对话的多 Agent 协作，支持人类参与 | 角色驱动的多 Agent 协作，上手简单 | Gateway 常驻进程 + 多 Session 并发，适合长时运行任务 |
| **记忆系统** | 自研分层存储（热/温/冷）+ 向量检索，支持 TB 级数据 | 提供多种记忆组件，但需自行集成和设计 | 主要依赖对话上下文，无内置长期记忆方案 | 主要依赖对话上下文，无内置长期记忆方案 | 支持跨会话的状态管理，但记忆方案相对基础 |
| **工具生态** | 原生集成 MCP、OpenClaw Skills，支持插件化扩展 | 拥有最庞大的第三方工具集成生态 | 支持函数调用，工具集成能力较强 | 支持工具调用，集成能力中等 | 核心优势在于其强大的 Skill 生态和浏览器自动化能力 |
| **可观测性** | 思维涟漪、全量事件溯源（心智时光机）、Langfuse 集成 | 依赖 LangSmith 等外部平台，自身能力较弱 | 提供基本的日志和调试功能 | 提供基本的日志和调试功能 | 提供详细的执行追踪和审计日志 |
| **生产就绪度** | 内置熔断、降级、配额、冷热数据分层等生产级特性 | 成熟稳定，大量企业级应用案例 | 处于快速发展期，部分功能稳定性有待提升 | 适合快速原型，生产级功能较弱 | 成熟稳定，在自动化场景下久经考验 |

总的来说，LangChain 以其庞大的生态和全面的功能成为综合性应用开发的首选；AutoGen 和 CrewAI 则在多 Agent 协作场景下各有千秋。而 **PromEngine 的独特价值在于**：

- **对“个人数字伙伴”场景的深度优化**：硅基/碳基双模式、认知燃料、心理急救等设计，让 Agent 更具“生命感”，超越了传统的工具属性。
- **本地优先、隐私第一**：默认使用本地模型和本地存储，用户数据永不离开设备，解决了企业级应用的合规痛点。
- **强大的生态复用能力**：原生集成 MCP 和 OpenClaw Skills 两大生态，让 PromEngine 一出世就站在巨人的肩膀上，避免了从零构建工具链的繁重工作。

---

## 核心 API 接口

### 聊天接口

| 方法   | 路径                          | 描述                                           |
| ------ | ----------------------------- | ---------------------------------------------- |
| POST   | `/api/v1/chat`                | 非流式对话，返回完整 `Response` 对象           |
| POST   | `/api/v1/chat/stream`         | 标准 SSE 流式响应（`text/event-stream`）       |
| POST   | `/api/v1/chat/stream-debug`   | 纯文本流式输出（调试用，无 SSE 包装）          |

**请求体示例** (`ChatRequest`)：

```json
{
  "sessionId": "demo-001",
  "message": "你好，请用一句话介绍你自己。",
  "taskType": "chat"
}
```

**非流式响应示例**：

```json
{
  "text": "我是一个拥有记忆功能的智能助手。",
  "processingTimeMs": 28400,
  "modelUsed": "gemma4-custom:q4",
  "cost": 0.0
}
```

### 配置管理接口

| 方法   | 路径                     | 描述                         |
| ------ | ------------------------ | ---------------------------- |
| GET    | `/api/v1/config`         | 获取当前用户配置视图         |
| GET    | `/api/v1/config/metadata`| 获取配置项元数据（供前端表单）|
| PATCH  | `/api/v1/config`         | 批量更新配置项               |
| POST   | `/api/v1/config/approve/{changeId}` | 批准待审批变更 |
| POST   | `/api/v1/config/rollback`| 回滚到指定版本               |

### 系统接口

| 方法   | 路径                     | 描述                       |
| ------ | ------------------------ | -------------------------- |
| GET    | `/api/v1/system/state`   | 获取 Agent 运行状态        |
| GET    | `/api/v1/system/health`  | 健康检查                   |

### WebSocket 端点

| 路径           | 描述                     |
| -------------- | ------------------------ |
| `/ws/ripple`   | 思维涟漪实时推送         |

---

## 如何参与开发与贡献

我们非常欢迎开发者参与到 PromEngine 的建设中来！无论你是修复一个 bug、添加一个新功能、完善文档，还是提供一个生态适配器，你的贡献都将被铭记。

### 贡献流程

1. **Fork 本项目** 到你的 GitHub 账户。
2. **创建特性分支**：`git checkout -b feature/your-awesome-feature`
3. **编写代码**，请遵循现有的代码风格和模块结构。
4. **添加测试**：确保你的改动不会破坏现有功能。
5. **提交变更**：使用清晰的 commit message，建议遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范。
6. **推送分支**：`git push origin feature/your-awesome-feature`
7. **发起 Pull Request**：描述清楚你的改动内容和动机，关联相关的 Issue。

### 开发环境搭建

1. 确保本地已安装 JDK 21+、Maven 3.8+ 以及一个可用的 IDE（推荐 IntelliJ IDEA）。
2. 克隆代码并导入 IDE，等待 Maven 依赖解析完成。
3. 在 `promengine-runtime/src/main/resources` 下复制 `application-dev.yml` 并修改为你的本地配置。
4. 运行 `PromEngineRuntimeApplication` 主类即可启动。

### 贡献方向建议

- **生态适配器**：为新的平台（如 Telegram、Discord）或工具（如特定的 MCP Server）开发适配器。
- **记忆存储后端**：实现 `MemoryBackend` 接口，支持新的向量数据库或对象存储。
- **认知模型**：扩展 `CognitivePhysiology`，添加新的防御机制或人格模型。
- **前端开发**：参与“璇玑台”的前端构建（React + TypeScript）。
- **文档完善**：修正错误、补充示例、翻译多语言文档。

### 代码规范

- Java 代码遵循 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)。
- 使用 Lombok 简化 POJO，但避免滥用 `@Builder` 在核心领域对象上。
- 所有公开 API 必须包含完整的 Javadoc 注释。
- 日志使用 Slf4j，级别合理（`debug` 用于诊断，`info` 用于关键节点）。

---

## 部署建议

- **内存**：建议分配至少 2GB 堆内存，若启用 LanceDB 向量索引，可能需要更多。
- **磁盘**：记忆数据按需扩展，冷存储建议挂载大容量 HDD。
- **生产配置**：使用 `application-prod.yml` 覆盖开发配置，确保 `devtools.debug-trace-enabled=false`。
- **监控**：集成 Prometheus + Grafana，指标端点默认 `/actuator/prometheus`。

---

## 许可证

本项目采用 **Apache License 2.0** 许可。详见 `LICENSE` 文件。

---

## 联系我们

- 官网：
- 邮箱：865494582@qq.com

---

*PromEngine — 你的数字伙伴，因你而进化。*