# PromEngine v6.3

**Your Digital Companion, Evolving with You.**

PromEngine is a lightweight, embeddable, self-evolving agent runtime framework. Guided by the core principles of "lightweight core, plugin-based extensibility, local-first priority, and model agnosticism," it delivers a one-stop solution encompassing memory storage, multi-agent collaboration, tool invocation, security governance, and autonomous scheduling.

Unlike simplistic "wrapper" tools, PromEngine features a built-in **five-tier memory system with forgetting curves, experience reuse, and autonomous distillation capabilities**, enabling agents to continuously learn and self-optimize through use, becoming a unique and trustworthy digital companion for every user.

**Key Strengths:**

*   **Architecturally supports petabyte-scale (TB-level) memory storage**, enabling long-term, persistent operation without data purges.
*   **Significantly enhanced audit and compliance capabilities**. It is no longer a black box; all execution operations are logged and traceable, supporting full auditing.
*   **Ecosystem compatibility** (fully compatible with MCP, SKILL, CLI, etc.).
*   **Enhanced REACT (R-CCAM)** cognitive loop.

---

## ✨ Core Features

### 🧠 A Memory System That Forgets and Refines

*   **Five-Tier Hybrid Memory**: Mimics the human brain by organizing memory into five distinct layers—Working, Episodic, Semantic, Procedural, and Collective—each with its own specialized role.
*   **Intelligent Forgetting Curve**: Memory strength naturally decays over time. Low-value information is automatically purged to prevent "knowledge obesity."
*   **Automatic Experience Distillation**: Successfully repeated operational patterns are automatically extracted as "Procedural Memory." When similar scenarios are encountered later, they can be directly reused, making the agent smarter over time.

### 🛠️ Declarative Tool System

*   **Register a Tool with a Single Annotation**: Developers simply add the `@ToolHandler` annotation, and the system automatically handles tool registration, JSON Schema generation, and version management. Tools from connected MCPs are also automatically registered in the unified tool management system for use by models.
*   **Safety First**: Local operations are automatically confined within a sandbox workspace, preventing security risks like path traversal.
*   **Versioned Canary Releases**: Supports multiple coexisting versions of tools, enabling A/B testing and traffic canarying.

### 🤖 Flexible Dual-Mode Orchestrator

*   **SIMPLE Mode**: A traditional "receive message → return response" pattern for fast responses.
*   **REACT (R-CCAM) Mode**: A structured cognitive loop following the Retrieval → Cognition → Control → Action → Memory phases, empowering the agent to autonomously use tools, perform multi-step reasoning, and record every thinking trajectory.

### 🧩 General and Code Memory Domains, Plus Custom Domains

*   **General Memory Domain**: Stores day-to-day conversations, user preferences, and generalized knowledge.
*   **Code Memory Domain**: Designed specifically for developers, this domain can automatically extract code structures (ASTs) to understand project architecture and assist with coding.
*   **Other Custom Memory Domains**: Additional memory domains, such as a Finance Domain or an Internal Corporate Knowledge Domain, can be added through metadata configuration. Tailored RAG enhancements can be implemented for different domains.

### 🔒 Enterprise-Grade Governance and Compliance

*   **TAME Dual-Track Scoring**: Each memory has two dimensions of scoring—"Utility" and "Safety"—to ensure data quality.
*   **Audit Logs**: The provenance of all operations is fully traceable, meeting compliance requirements.
*   **Cost Budget Controls**: Configurable daily/monthly API call budgets are available, automatically tripping circuit breakers when thresholds are met.

### 🔗 Ecosystem Compatibility

*   **MCP**: Full support for MCP services.
*   **CLI**: Supports custom tools and is directly adaptable to various CLIs.
*   **SKILL**: Supports reading and adaptation of `skill.md` content format.

---

## 🛠️ Technology Stack

| Tier | Technology |
|------|------|
| **Backend Framework** | Spring Boot 3.2.5 |
| **Language** | Java 21 |
| **AI Integration** | Spring AI 1.0.0-M7 |
| **Local Model** | Ollama |
| **Vector Storage** | ChromaDB |
| **Relational Storage** | SQLite (Hot Data) + Apache Parquet (Warm/Cold Data) |
| **Full-Text Indexing** | Apache Lucene 9.10.0 |
| **Graph Database** | Neo4j (Optional) |
| **Sandbox Isolation** | Chicory (Wasm Runtime) |
| **Frontend** | Vue 3 + Vite + Tailwind CSS |
| **Build Tool** | Maven |

---

## 📁 Project Structure

```
promengine/
├── promengine-core/            # Core Interfaces & Domain Models
├── promengine-memory/          # Aeon Five-Tier Memory System
├── promengine-model/           # Model Gateway & Multi-Model Adapter
├── promengine-executor/        # Execution Orchestrator
├── promengine-skill/           # Skill Management
├── promengine-cognition/       # Cognitive Physiology Layer (Silicon/Carbon Dual Modes)
├── promengine-swarm/           # Micro-Agent Cluster Scheduling
├── promengine-neuro/           # Metacognition & Thinking Ripples
├── promengine-temporal/        # Subjective Time Perception
├── promengine-verifier/        # Formal Verification & Security Sandbox
├── promengine-ethics/          # Ethical Decision-Making & Auditing
├── promengine-prompt/          # Prompt Pipeline
├── promengine-apex/            # API Cost Control Center
├── promengine-identity-proxy/  # Digital Identity Proxy
├── promengine-psych-aid/       # Psychological First Aid Module
├── promengine-devtools/        # Developer Tools
├── promengine-runtime/         # Runtime Assembly
├── promengine-spring-boot-starter/  # Auto-Configuration Starter
├── promengine-ecosystem/       # Ecosystem Adapter Aggregation
│   ├── promengine-adapter-litellm/
│   ├── promengine-adapter-mcp/
│   ├── promengine-adapter-openclaw/
│   ├── promengine-adapter-browseruse/
│   └── promengine-adapter-feishu/
└── promengine-web/             # Web Layer & REST API
```

---

## 🚀 Quick Start

### Requirements

*   **JDK 21** or higher
*   **Maven 3.8+**
*   **Node.js 18+** (Frontend)
*   **Docker** (For running ChromaDB, optional)
*   **Ollama** (Local model runtime)

### 1. Pull and Start Required Services

```bash
# Start ChromaDB (Vector Database)
docker run -d -p 8000:8000 chromadb/chroma

# Pull Ollama models
ollama pull gemma4-custom:q4
ollama pull nomic-embed-text   # For generating text embeddings
```

### 2. Start the Backend

```bash
cd promengine
mvn clean install -DskipTests
cd promengine-web
mvn spring-boot:run
```

The backend runs on `http://localhost:8080` by default. A PromEngine ASCII art banner will be displayed upon successful startup.

### 3. Start the Frontend

```bash
cd promengineV3-vue
npm install
npm run dev
```

The frontend runs on `http://localhost:3000`. Open your browser to access Xuanji Tai (璇玑台).

---

## 📖 User Documentation

### 💬 Chat

The Chat page is opened by default and supports:
*   **Streaming Chat**: Displays model responses in real-time.
*   **Thinking Process**: When the model's thinking mode is enabled, the chain of thought can be expanded for viewing.
*   **Tool Invocation**: The agent automatically selects the appropriate tool based on your request.
*   **Save to Memory, Export to Image/PDF, Share Link**: Available via the top-right menu on each message bubble.

### 🧠 Memory Management

The Memory Management page offers three sub-views:

| View | Function |
|------|------|
| **Layer Browser** | Browse all memories across the five layers (Working/Episodic/Semantic/Procedural/Collective). Supports searching and marking entries as high-quality or deprecated. |
| **Retrieval Debugger** | Test memory retrieval effectiveness and view hit details from different pathways (Hot Storage, Lucene, Vector). |
| **Quality Workshop** | Manage pending review memories and high-score memories. Supports batch marking and forgetting curve simulations. |

### 🤖 Agent and Skill

*   **Agent Management**: Create, configure, enable, and disable intelligent agents. Supports defining Independent Lifeforms (Carbon Mode) and proactivity levels.
*   **Agent Group Chat**: Create groups, assign roles to different Agents, and observe autonomous multi-agent discussions.
*   **Skill Management**: Create, import, and edit skills. Supports installing community Skills from the MCP marketplace.

### 🔧 Tool Workshop

View all registered tools, including their name, description, category, and execution location. Supports:
*   **Enable/Disable Tools** (takes effect immediately, no restart needed).
*   **Tool Testing Sandbox**: Fill in parameters, run a tool trial, and view the returned result.
*   **View Details**: Inspect parameter schemas and invocation statistics.

### ⚙️ Configuration Center

Visually configure parameters for the model gateway, memory strategy, sandbox, and cost budget.

---

## 📡 API Interface Overview
*Note: Only a subset of interfaces is listed.*

| Path | Method | Description |
|------|------|------|
| `/api/v1/chat` | POST | Synchronous Chat |
| `/api/v1/chat/stream` | POST | Streaming Chat (SSE) |
| `/api/v1/chat/sessions` | GET | Get session list |
| `/api/v1/memory/layers` | GET | Get memory layers |
| `/api/v1/memory/layer/{name}` | GET | Get memories for a specific layer |
| `/api/v1/tools` | GET | Get tool list |
| `/api/v1/tools/{name}/test` | POST | Test a tool |
| `/api/v1/skills` | GET/POST | Skill management |
| `/api/v1/agents` | GET/POST | Agent management |
| ... | ... | ... |

---

## 📸 Frontend Screenshots

<img width="1864" height="1234" alt="ezgif-498edafded01723f" src="https://github.com/user-attachments/assets/fa40500f-4a25-4a8d-81fb-3cb2bac2cc1d" />

*   **Chat Interface**: Main chat area on the left, a collapsible session history list on the right, and an input box with a thinking ripple indicator at the bottom.
*   **Memory Management**: Five-tier memory browsing, detail drawer, and quality workshop.
*   **Agent Management**: Agent list, group chat interface, and configuration drawer.
*   **Tool Workshop**: Tool cards, testing sandbox, and detail panel.

---

## 📄 License

This project is open-sourced under the **Apache License 2.0**.

---

## 🔗 Related Links

*   **Backend Project**: `promengine/`
*   **Frontend Project**: `promengineV3-vue/` [https://github.com/AeolusJack/promengineV3-vue.git](https://github.com/AeolusJack/promengineV3-vue.git)
*   **Ollama**: [https://ollama.com](https://ollama.com)
*   **ChromaDB**: [https://www.trychroma.com](https://www.trychroma.com)
*   **Spring AI**: [https://spring.io/projects/spring-ai](https://spring.io/projects/spring-ai)

---
## 🤝 How to Contribute

PromEngine is an open and inclusive open-source project, and we wholeheartedly welcome contributions of any kind! Whether you are an experienced developer or just starting out with AI agents, your participation helps this "digital companion" grow smarter and more capable.

### Getting Started

1. **Fork the Repository**: Click the `Fork` button in the upper-right corner of the page to copy the repository to your own GitHub account.
2. **Clone Locally**: `git clone https://github.com/your-username/promengineV3.git`
3. **Create a Feature Branch**: `git checkout -b feat/your-feature-description`. We recommend using branch name prefixes such as `feat/` (new feature), `fix/` (bug fix), or `docs/` (documentation).
4. **Start Developing**: Add or modify code according to the project structure. Please ensure your code follows the existing code style and add necessary unit tests for core logic.
5. **Commit Your Changes**: Use clear and concise commit messages. We recommend following the [Conventional Commits](https://www.conventionalcommits.org/) specification.
6. **Open a Pull Request (PR)**: After pushing your branch to GitHub, open a PR against the original repository. Please describe in detail what changes you made, what problem you solved, or what feature you implemented in the PR description.
7. **Code Review**: The project maintainers will review your PR and may suggest some modifications. Please follow up patiently until your PR is merged.

### Contribution Areas

You can contribute your talents in the following areas:

*   **🛠️ Develop New Tools**: Create new tools (such as browser automation, data analysis, API calls, etc.) using the `@ToolHandler` annotation to extend the Agent's capabilities.
*   **🧠 Enhance the Memory System**: Optimize retrieval algorithms, forgetting curve strategies, or knowledge distillation logic within the five-tier memory system.
*   **🔌 Adapt New Models / Platforms**: Add new model providers to the ModelGateway, or add new platform adapters (e.g., DingTalk, Discord) to the ecosystem.
*   **🐛 Fix Bugs**: Find bugs you're interested in on the [Issues](https://github.com/AeolusJack/promengineV3/issues) page and fix them.
*   **📝 Improve Documentation**: Improve or translate the project documentation to help more people get started easily.
*   **🎨 Optimize Frontend Experience**: Refine the UI/UX design of Xuanji Tai (璇玑台) to make it more visually appealing and user-friendly.
*   **🧪 Write Tests**: Add unit tests and integration tests for existing features to improve the project's robustness.

### Development Guide

*   **Backend Development**: Please ensure you have JDK 21 and Maven 3.8+ installed locally. After importing the project, it is recommended to first read through the interface definitions in the `promengine-core` module to understand the responsibilities of each module.
*   **Frontend Development**: Navigate to the `promengineV3-vue` directory and run `npm install && npm run dev` to start the development server. The frontend uses Vue 3 + TypeScript, with an interface style that follows a minimalist blue-white-gray aesthetic.
*   **Extending the Memory System**: If you wish to add new memory layers or domains, please read the relevant documentation for `MemoryMetadataRegistry` and `AeonMemoryProperties` within the `promengine-memory` module.
*   **Code Standards**: Please ensure your code follows the project's existing coding style and includes clear Javadoc or TypeScript comments for public APIs.

### Community and Contact

*   **GitHub Issues**: [https://github.com/AeolusJack/promengine/issues](https://github.com/AeolusJack/promengineV3/issues) (Submit bugs and feature suggestions)
*   **GitHub Discussions**: [https://github.com/AeolusJack/promengine/discussions](https://github.com/AeolusJack/promengineV3/discussions) (Technical discussions and Q&A)
*   **Email**: `pengjingqing125@gmail.com` (Author's email for non-public communication)

Whether you fix a typo or implement a complex MCP service, your name will appear in the contributors' list. PromEngine evolves with you—we look forward to your Pull Request!


**PromEngine — Your Digital Companion, Evolving with You.**



---

# PromEngine v6.3  中文版文档

**你的数字伙伴，因你而进化。**

PromEngine 是一个轻量级、可嵌入、自我进化的智能体运行时框架。它以“轻核心、插件化、本地优先、模型无关”为核心理念，提供从记忆存储、多 Agent 协作、工具调用、安全管控到自主调度的一站式解决方案。

不同于简单的“套壳”工具，PromEngine 内置了**具备遗忘曲线、经验复用和自主蒸馏能力的五层记忆系统**，让 Agent 能在使用中持续学习、自我优化，成为每个用户独一无二且值得信赖的数字伙伴。

重点问题解决： 

从架构设计上就已支持TB级的记忆存储数据，完全可以长期不删档运行。   

重点进行了审计合规增强，不再是黑盒，对于所有执行操作都有轨迹留痕，可支持审计。

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

## 🤝 参与贡献 (How to Contribute)

PromEngine 是一个开放、包容的开源项目，我们由衷欢迎任何形式的贡献！无论你是经验丰富的开发者，还是刚刚接触 AI Agent 的新手，你的每一次参与都让这个“数字伙伴”变得更加聪明和强大。

### 如何开始？

1. **Fork 本仓库**：点击页面右上角的 `Fork` 按钮，将代码仓库复制到你自己的 GitHub 账户下。
2. **克隆到本地**：`git clone https://github.com/你的用户名/promengineV3.git`
3. **创建特性分支**：`git checkout -b feat/你的功能描述`。分支命名建议使用 `feat/`（新功能）、`fix/`（修复）、`docs/`（文档）等前缀。
4. **进行开发**：按照项目结构添加或修改代码。请确保你的代码符合现有的代码风格，并为核心逻辑添加必要的单元测试。
5. **提交变更**：使用清晰、简洁的提交信息。我们推荐遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范。
6. **发起 Pull Request (PR)**：将你的分支推送到 GitHub 后，在原仓库发起 PR。请在 PR 描述中详细说明你做了哪些改动、解决了什么问题或实现了什么功能。
7. **代码评审**：项目维护者会对 PR 进行评审，可能会提出一些修改建议。请耐心跟进，直到 PR 被合并。

### 贡献方向

你可以在以下方向贡献你的才华：

*   **🛠️ 开发新工具**：使用 `@ToolHandler` 注解创建新的工具（如浏览器自动化、数据分析、API 调用等），扩展 Agent 的能力。
*   **🧠 增强记忆系统**：优化五层记忆的检索算法、遗忘曲线策略或知识蒸馏逻辑。
*   **🔌 适配新模型/平台**：为 ModelGateway 增加新的模型提供商，或为生态系统增加新的平台适配器（如钉钉、Discord）。
*   **🐛 修复 Bug**：在 [Issues](https://github.com/你的组织/promengineV3/issues) 页面找到你感兴趣的 bug 进行修复。
*   **📝 完善文档**：改进或翻译本项目文档，让更多人能轻松上手。
*   **🎨 优化前端体验**：完善璇玑台 (Xuanji Tai) 的 UI/UX 设计，使其更加美观易用。
*   **🧪 编写测试**：为现有功能增加单元测试、集成测试，提升项目健壮性。

### 开发指南

*   **后端开发**：请确保本地已安装 JDK 21 和 Maven 3.8+。导入项目后，建议先完整阅读 `promengine-core` 模块中的接口定义，理解各模块的职责。
*   **前端开发**：进入 `promengineV3-vue` 目录，执行 `npm install && npm run dev` 启动开发服务器。前端采用 Vue 3 + TypeScript，界面风格遵循蓝白灰极简美学。
*   **记忆系统扩展**：如果你希望增加新的记忆层或域，请阅读 `promengine-memory` 模块下的 `MemoryMetadataRegistry` 和 `AeonMemoryProperties` 相关文档。
*   **代码规范**：请确保你的代码遵循项目现有的编码风格，并为公共 API 添加清晰的 Javadoc 或 TypeScript 注释。

### 社区与联系方式

*   **GitHub Issues**: [https://github.com/AeolusJack/promengineV3/issues](https://github.com/AeolusJack/promengineV3/issues) (提交 Bug、功能建议)
*   **GitHub Discussions**: [https://github.com/AeolusJack/promengineV3/discussions](https://github.com/AeolusJack/promengineV3/discussions) (技术讨论、问答)
*   **邮箱**：`pengjingqing125@gmail.com` (作者邮箱，用于非公开沟通)

无论你是修复了一个拼写错误，还是实现了一个复杂的 MCP 服务，你的名字都将出现在贡献者名单中。PromEngine 因你而进化，期待你的 Pull Request！

**PromEngine — 你的数字伙伴，因你而进化。**
