-- 热存储 SQLite 表结构定义
CREATE TABLE IF NOT EXISTS hot_memory (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    timestamp INTEGER NOT NULL,          -- epoch milliseconds
    memory_type TEXT NOT NULL,           -- EPISODIC, SEMANTIC, PROCEDURAL
    importance REAL DEFAULT 0.5,
    metadata TEXT,                       -- JSON 格式
    ttl_seconds INTEGER,
    deleted INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    deleted_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_hot_user_time ON hot_memory(user_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_hot_deleted ON hot_memory(deleted);
CREATE INDEX IF NOT EXISTS idx_hot_content ON hot_memory(content); -- SQLite FTS 可单独配置，此处简化