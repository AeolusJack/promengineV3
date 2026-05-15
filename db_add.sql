ALTER TABLE agents ADD COLUMN enable_human_review INTEGER DEFAULT 0;          -- 是否启用人机协作审核
ALTER TABLE agents ADD COLUMN workflow_template_id TEXT;                      -- 关联的工作流模板 ID
ALTER TABLE agents ADD COLUMN knowledge_config TEXT;                          -- JSON，知识注入配置
ALTER TABLE agents ADD COLUMN tool_overrides TEXT;                            -- JSON，工具参数覆盖配置
ALTER TABLE agents ADD COLUMN custom_metadata TEXT;                           -- JSON，用户自定义元数据
ALTER TABLE agents ADD COLUMN max_retries INTEGER DEFAULT 3;                  -- 最大重试次数
ALTER TABLE agents ADD COLUMN timeout_seconds INTEGER DEFAULT 300;            -- 超时时间（秒）
ALTER TABLE agents ADD COLUMN fallback_agent_id TEXT;                         -- 降级或备选 Agent ID




CREATE TABLE IF NOT EXISTS agent_workflows (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT DEFAULT '',
    version TEXT DEFAULT '1.0.0',
    steps TEXT NOT NULL,                        -- JSON 数组，定义步骤顺序与类型
    triggers TEXT DEFAULT '{}',                 -- JSON，触发条件配置
    max_steps INTEGER DEFAULT 10,
    timeout_seconds INTEGER DEFAULT 600,
    fallback_strategy TEXT DEFAULT 'stop',      -- stop / retry / skip
    created_by TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);


-- 字段说明：

-- steps：工作流步骤序列，例如 [{"name":"parse_code","tool":"ast_parser"},{"name":"analyze","tool":"dep_analyzer"}]。

-- triggers：定义自动触发的条件，如定时触发、消息触发。

-- fallback_strategy：步骤失败时的处理策略。

CREATE TABLE IF NOT EXISTS agent_knowledge_bases (
    id TEXT PRIMARY KEY,
    agent_id TEXT NOT NULL,
    name TEXT NOT NULL,
    type TEXT NOT NULL,                         -- vector / graph / rule_file / memory_domain
    config TEXT NOT NULL,                       -- JSON，具体连接配置
    priority INTEGER DEFAULT 0,                 -- 优先级，数值越大越先检索
    enabled INTEGER DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE
);
--  设计思路
-- type 支持多种知识源：向量数据库、图数据库、规则文件、记忆域。

--  config 通过 JSON 灵活定义连接字符串、索引名、文件路径等。

--  priority 用于检索时的排序，优先从重要知识库获取上下文。

CREATE TABLE IF NOT EXISTS agent_tool_bindings (
    id TEXT PRIMARY KEY,
    agent_id TEXT NOT NULL,
    tool_name TEXT NOT NULL,                    -- 工具名称（对应 ToolRegistry）
    config TEXT DEFAULT '{}',                   -- JSON，工具执行时的额外参数
    enabled INTEGER DEFAULT 1,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_tool_bindings_agent ON agent_tool_bindings(agent_id);


-- 说明：

-- 将 Agent 与工具的关系从原来的 tools JSON 字段中独立出来，便于管理和统计分析。

-- config 允许为每个 Agent 定制工具的参数（如 API key、超时时间）。


CREATE TABLE IF NOT EXISTS agent_execution_logs (
    id TEXT PRIMARY KEY,
    agent_id TEXT NOT NULL,
    session_id TEXT,
    task_id TEXT,
    step_name TEXT,
    status TEXT NOT NULL,                       -- running / success / failed / skipped
    input TEXT,                                 -- JSON
    output TEXT,                                -- JSON
    error_message TEXT,
    start_time INTEGER NOT NULL,
    end_time INTEGER,
    duration_ms INTEGER,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_exec_logs_agent ON agent_execution_logs(agent_id);
CREATE INDEX IF NOT EXISTS idx_exec_logs_session ON agent_execution_logs(session_id);


-- 用途：

-- 记录 Agent 每次工具调用或任务执行的详细情况，用于审计、性能分析和错误追踪。

-- input 和 output 存储 JSON 格式数据，方便调试和回溯。


CREATE TABLE IF NOT EXISTS agent_human_reviews (
    id TEXT PRIMARY KEY,
    agent_id TEXT NOT NULL,
    session_id TEXT,
    task_id TEXT,
    request_type TEXT NOT NULL,                 -- confirmation / input / choice
    request_data TEXT NOT NULL,                 -- JSON，待审核内容或选项
    response_data TEXT,                         -- JSON，人工响应结果
    status TEXT DEFAULT 'pending',              -- pending / approved / rejected / timeout
    created_at INTEGER NOT NULL,
    resolved_at INTEGER,
    FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_reviews_agent ON agent_human_reviews(agent_id);

-- 人机协作流程：

-- Agent 通过 WebSocket 推送 confirmation_required 事件，同时写入此表。

-- 前端审核后，更新 response_data 和 status，Agent 继续执行。
