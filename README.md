# PromEngine v6.3

**你的数字伙伴，因你而进化。**

PromEngine 是一个轻量级、可嵌入、自我进化的智能体运行时框架。它以“轻核心、插件化、本地优先、模型无关”为核心理念，提供从记忆存储、多 Agent 协作、工具调用、安全管控到自主调度的一站式解决方案。

不同于简单的“套壳”工具，PromEngine 内置了**具备遗忘曲线、经验复用和自主蒸馏能力的五层记忆系统**，让 Agent 能在使用中持续学习、自我优化，成为每个用户独一无二且值得信赖的数字伙伴。

重点问题解决： 

从架构设计上就已支持TB级的记忆存储数据，完全可以长期不删档运行。   

重点进行了审计合规增强，不在是黑盒，对于所以执行操作都有轨迹留痕，可支持审计。

生态兼容（MCP、SKILL、CLI等完全兼容）。

增强型REACT（R-CCAM）。




---

## ✨ 核心特性

### 🧠 会遗忘、会提炼的记忆系统

- **五层混合记忆**：模拟人脑，将记忆分为工作、情景、语义、过程和集体五个层级，各司其职。
- **智能遗忘曲线**：记忆强度随时间自然衰减，低价值信息自动清理，避免“知识肥胖”。
- **经验自动蒸馏**：多次成功的操作模式会被自动提炼为“过程记忆”，下次遇到相似场景可直接复用，越用越聪明。

### 🛠️ 声明式工具系统

- **一个注解即可注册工具**：开发者只需添加 `@ToolHandler` 注解，系统自动完成工具注册、JSON Schema 生成和版本管理，并且对于连接的MCP，会将其对应的工具自动注册到统一的工具管理上，供模型使用。
- **安全第一**：本地操作自动限定在沙箱工作区内，防止路径穿越等安全风险。
- **版本灰度发布**：支持工具多版本并存，可进行 A/B 测试和流量灰度。

### 🤖 灵活的双模式编排器

- **SIMPLE 模式**：传统的“接收消息-返回回复”模式，快速响应。
- **REACT (R-CCAM) 模式**：检索→认知→控制→行动→记忆的结构化认知循环，让 Agent 能自主使用工具、多步推理，并记录每一次思考轨迹。

### 🧩 通用与代码双域记忆及可自定义其他记忆域

- **通用记忆域**：存储日常对话、用户偏好等泛化知识。
- **代码记忆域**：专为开发者设计，可自动抽取代码结构（AST），理解项目架构，辅助编码。
- **其他记忆域**：可通过元数据配置，增加其他记忆域，比如金融记忆域，公司内部知识记忆域等，根据不同的记忆域，自定义实现不同RAG增强。


### 🔒 企业级治理与合规

- **TAME 双轨评分**：每条记忆都有“效用”和“安全”两个维度的评分，确保数据质量。
- **审计日志**：所有操作的来源可追溯，满足合规要求。
- **成本预算控制**：可配置日/月 API 调用预算，到达阈值自动熔断。

###  生态兼容性

- **MCP**：完全支持MCP服务。
- **CLI**：支持自定义工具，可直接适配各种CLI。
- **SKILL**：支持skill.md内容格式的读取和适配。


---

## 🛠️ 技术栈

| 层级 | 技术 |
|------|------|
| **后端框架** | Spring Boot 3.2.5 |
| **语言** | Java 21 |
| **AI 集成** | Spring AI 1.0.0-M7 |
| **本地模型** | Ollama |
| **向量存储** | ChromaDB |
| **关系存储** | SQLite (热数据) + Apache Parquet (温/冷数据) |
| **全文索引** | Apache Lucene 9.10.0 |
| **图数据库** | Neo4j (可选) |
| **沙箱隔离** | Chicory (Wasm 运行时) |
| **前端** | Vue 3 + Vite + Tailwind CSS |
| **构建工具** | Maven |

---

## 📁 项目结构

```
promengine/
├── promengine-core/            # 核心接口与领域模型
├── promengine-memory/          # Aeon 五层记忆系统
├── promengine-model/           # 模型网关与多模型适配
├── promengine-executor/        # 执行编排器 (Orchestrator)
├── promengine-skill/           # 技能管理
├── promengine-cognition/       # 认知生理层 (硅基/碳基双模式)
├── promengine-swarm/           # 微 Agent 集群调度
├── promengine-neuro/           # 元认知与思维涟漪
├── promengine-temporal/        # 主观时间感知
├── promengine-verifier/        # 形式化验证与安全沙箱
├── promengine-ethics/          # 伦理决策与审计
├── promengine-prompt/          # 提示词管线
├── promengine-apex/            # API 成本管控中心
├── promengine-identity-proxy/  # 数字身份代理
├── promengine-psych-aid/       # 心理急救模块
├── promengine-devtools/        # 开发者工具
├── promengine-runtime/         # 运行时组装
├── promengine-spring-boot-starter/  # 自动配置 Starter
├── promengine-ecosystem/       # 生态适配器聚合
│   ├── promengine-adapter-litellm/
│   ├── promengine-adapter-mcp/
│   ├── promengine-adapter-openclaw/
│   ├── promengine-adapter-browseruse/
│   └── promengine-adapter-feishu/
└── promengine-web/             # Web 层与 REST API
```

---

## 🚀 快速开始

### 环境要求

- **JDK 21** 或更高版本
- **Maven 3.8+**
- **Node.js 18+** (前端)
- **Docker** (用于运行 ChromaDB，可选)
- **Ollama** (本地模型运行)

### 1. 拉取并启动所需服务

```bash
# 启动 ChromaDB (向量数据库)
docker run -d -p 8000:8000 chromadb/chroma

# 拉取 Ollama 模型
ollama pull gemma4-custom:q4
ollama pull nomic-embed-text   # 用于生成文本向量
```

### 2. 启动后端

```bash
cd promengine
mvn clean install -DskipTests
cd promengine-web
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`。启动成功后会显示 PromEngine ASCII 艺术字横幅。

### 3. 启动前端

```bash
cd promengineV3-vue
npm install
npm run dev
```

前端运行在 `http://localhost:3000`。打开浏览器即可访问璇玑台。

---

## 📖 使用文档

### 💬 对话

默认进入对话页面。支持：
- **流式对话**：实时显示模型回复。
- **思考过程**：当模型启用思考模式时，可展开查看思维链。
- **工具调用**：Agent 会根据你的请求自动选择合适的工具。
- **存入记忆**、**导出长图/PDF**、**分享链接**：每条消息右上角菜单提供。

### 🧠 记忆管理

记忆管理页面提供三个子视图：

| 视图 | 功能 |
|------|------|
| **分层浏览** | 按工作/情景/语义/过程/集体五层浏览所有记忆，支持搜索、标注优质/废弃。 |
| **检索调试** | 测试记忆检索效果，查看各通路（热存储、Lucene、向量）命中详情。 |
| **质量工坊** | 管理待审核记忆和高分记忆，支持批量优质/废弃，遗忘曲线模拟。 |

### 🤖 Agent 与 Skill

- **Agent 管理**：创建、配置、启用/禁用智能体。支持设置独立生命体（碳基模式）和主动性级别。
- **Agent 群聊**：创建群组，赋予不同 Agent 角色，观察多 Agent 自主讨论。
- **Skill 管理**：创建、导入、编辑技能。支持从 MCP 市场安装社区 Skill。

### 🔧 工具工坊

查看所有已注册的工具，包括名称、描述、分类、执行位置。支持：
- **启用/禁用工具**（即时生效，无需重启）
- **工具测试台**：填写参数，试运行工具，查看返回结果
- **查看详情**：参数 Schema 和调用统计

### ⚙️ 配置中心

可视化配置模型网关、记忆策略、沙箱设置、成本预算等参数。

---

## 📡 API 接口概览
注意：只列出了部分接口

| 路径 | 方法 | 说明 |
|------|------|------|
| `/api/v1/chat` | POST | 同步对话 |
| `/api/v1/chat/stream` | POST | 流式对话 (SSE) |
| `/api/v1/chat/sessions` | GET | 获取会话列表 |
| `/api/v1/memory/layers` | GET | 获取记忆层级 |
| `/api/v1/memory/layer/{name}` | GET | 获取指定层级记忆 |
| `/api/v1/tools` | GET | 获取工具列表 |
| `/api/v1/tools/{name}/test` | POST | 测试工具 |
| `/api/v1/skills` | GET/POST | 技能管理 |
| `/api/v1/agents` | GET/POST | Agent 管理 |
 ·······
---

## 📸 前端页面展示

<img width="1864" height="1234" alt="ezgif-498edafded01723f" src="https://github.com/user-attachments/assets/fa40500f-4a25-4a8d-81fb-3cb2bac2cc1d" />

- 对话界面：左侧对话区，右侧可折叠历史会话列表，底部输入框和思维涟漪。
- 记忆管理：五层记忆浏览、详情抽屉、质量工坊。
- Agent 管理：Agent 列表、群聊界面、配置抽屉。
- 工具工坊：工具卡片、测试台、详情面板。

---

## 📄 许可证

本项目采用 **Apache License 2.0** 开源协议。

---

## 🔗 相关链接

- **后端项目**：`promengine/`
- **前端项目**：`promengineV3-vue/` https://github.com/AeolusJack/promengineV3-vue.git
- **Ollama**：[https://ollama.com](https://ollama.com)
- **ChromaDB**：[https://www.trychroma.com](https://www.trychroma.com)
- **Spring AI**：[https://spring.io/projects/spring-ai](https://spring.io/projects/spring-ai)

---

**PromEngine — 你的数字伙伴，因你而进化。**
