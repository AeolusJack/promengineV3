项目结构
````
promengineV3/
├── promengine-parent/                 # 原有父POM
├── promengine-core/                   # 核心接口与抽象层
├── promengine-memory/                 # Aeon记忆系统
├── promengine-model/                  # 模型网关
├── promengine-executor/               # 编排器与工具
├── promengine-cognition/              # 认知生理模块
├── promengine-ethics/                 # 伦理模块
├── promengine-ecosystem/              # 生态适配器
│   ├── promengine-adapter-mcp/        # MCP适配器
│   └── ...
├── promengine-web/                    # Web层
│
├── promengine-agent/                  # ★ 新增：Agent聚合模块
│   ├── pom.xml                        # 聚合POM，管理所有子模块
│   ├── promengine-agent-common/       # ★ 通用能力共享模块
│   │   └── src/main/java/
│   │       └── com/thirdexploration/promengine/agent/common/
│   │           ├── knowledge/          # 知识注入接口
│   │           ├── report/             # 报告模板引擎
│   │           ├── workflow/           # 工作流编排
│   │           ├── collaborator/       # 人机协作事件模型
│   │           └── assembly/           # Agent装配工厂
│   │
│   ├── promengine-agent-code/         # 代码Agent
│   │   └── src/main/java/
│   │       └── com/thirdexploration/promengine/agent/code/
│   │           ├── tool/               # 代码工具集
│   │           ├── graph/              # 代码图谱构建
│   │           └── template/           # 代码模板引擎
│   │
│   └── promengine-agent-finance/      # 金融Agent
│       └── src/main/java/
│           └── com/thirdexploration/promengine/agent/finance/
│               ├── tool/              # 金融工具集
│               ├── graph/             # 金融知识图谱
│               └── template/          # 金融模板引擎
````


通用能力沉淀清单    
promengine-agent-common 模块需包含：

组件	说明	来源      
KnowledgeImporter	知识注入标准化接口	从代码Agent抽象  
ReportGenerator	结构化报告模板引擎	从分析报告抽象     
WorkflowOrchestrator	通用工作流编排器	从Agent Loop抽象       
ConfirmationHandler	人机协作确认处理器	从Diff确认抽象       
AgentAssemblyService	Agent装配工厂	从配置装配抽象     
ScheduledTaskManager	定时任务调度器	从定时分析抽象     