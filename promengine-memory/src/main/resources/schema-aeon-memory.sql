-- =====================================================
-- Aeon 全域认知记忆系统 - 数据库 Schema (SQLite 兼容版)
-- =====================================================

-- 1. 情景记忆表 (L2)
CREATE TABLE IF NOT EXISTS episodic_memory (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    timestamp INTEGER NOT NULL,
    memory_type TEXT NOT NULL DEFAULT 'episodic',
    importance REAL DEFAULT 0.5,
    metadata TEXT,
    ttl_seconds INTEGER,
    domain TEXT DEFAULT 'general',
    project_id TEXT,
    strength REAL DEFAULT 1.0,
    layer TEXT DEFAULT 'episodic',
    utility_score REAL DEFAULT 0.5,
    safety_score REAL DEFAULT 0.9,
    sharing_level TEXT DEFAULT 'private',
    provenance TEXT,
    retrieval_count INTEGER DEFAULT 0,
    session_id TEXT,
    deleted INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    deleted_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_episodic_user_time ON episodic_memory(user_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_episodic_domain ON episodic_memory(domain);
CREATE INDEX IF NOT EXISTS idx_episodic_layer ON episodic_memory(layer);
CREATE INDEX IF NOT EXISTS idx_episodic_deleted ON episodic_memory(deleted);
CREATE INDEX IF NOT EXISTS idx_episodic_session ON episodic_memory(session_id);

-- 2. 语义记忆表 (L3)
CREATE TABLE IF NOT EXISTS semantic_memory (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    timestamp INTEGER NOT NULL,
    memory_type TEXT NOT NULL DEFAULT 'semantic',
    importance REAL DEFAULT 0.5,
    metadata TEXT,
    domain TEXT DEFAULT 'general',
    project_id TEXT,
    strength REAL DEFAULT 1.0,
    layer TEXT DEFAULT 'semantic',
    utility_score REAL DEFAULT 0.5,
    safety_score REAL DEFAULT 0.9,
    sharing_level TEXT DEFAULT 'private',
    provenance TEXT,
    retrieval_count INTEGER DEFAULT 0,
    vector_id TEXT,
    session_id TEXT,
    deleted INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    deleted_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_semantic_user ON semantic_memory(user_id);
CREATE INDEX IF NOT EXISTS idx_semantic_domain ON semantic_memory(domain);
CREATE INDEX IF NOT EXISTS idx_semantic_strength ON semantic_memory(strength);
CREATE INDEX IF NOT EXISTS idx_semantic_deleted ON semantic_memory(deleted);

-- 3. 过程记忆表 (L4)
CREATE TABLE IF NOT EXISTS procedural_memory (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    timestamp INTEGER NOT NULL,
    memory_type TEXT NOT NULL DEFAULT 'procedural',
    importance REAL DEFAULT 0.5,
    metadata TEXT,
    domain TEXT DEFAULT 'general',
    project_id TEXT,
    strength REAL DEFAULT 1.0,
    layer TEXT DEFAULT 'procedural',
    utility_score REAL DEFAULT 0.5,
    safety_score REAL DEFAULT 0.9,
    sharing_level TEXT DEFAULT 'private',
    provenance TEXT,
    retrieval_count INTEGER DEFAULT 0,
    trigger_condition TEXT,
    reliability REAL DEFAULT 0.7,
    session_id TEXT,
    deleted INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_procedural_user_trigger ON procedural_memory(user_id, trigger_condition);
CREATE INDEX IF NOT EXISTS idx_procedural_reliability ON procedural_memory(reliability);

-- 4. 集体记忆表 (L5)
CREATE TABLE IF NOT EXISTS collective_memory (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    timestamp INTEGER NOT NULL,
    memory_type TEXT NOT NULL DEFAULT 'collective',
    importance REAL DEFAULT 0.5,
    metadata TEXT,
    domain TEXT DEFAULT 'general',
    project_id TEXT,
    strength REAL DEFAULT 1.0,
    layer TEXT DEFAULT 'collective',
    utility_score REAL DEFAULT 0.5,
    safety_score REAL DEFAULT 0.9,
    sharing_level TEXT DEFAULT 'domain',
    provenance TEXT,
    retrieval_count INTEGER DEFAULT 0,
    deleted INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_collective_domain_sharing ON collective_memory(domain, sharing_level);
CREATE INDEX IF NOT EXISTS idx_collective_utility ON collective_memory(utility_score DESC);

-- 5. 因果关联表
CREATE TABLE IF NOT EXISTS causal_links (
    id TEXT PRIMARY KEY,
    source_memory_id TEXT NOT NULL,
    target_memory_id TEXT NOT NULL,
    relation_type TEXT NOT NULL,
    confidence REAL DEFAULT 1.0,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (source_memory_id) REFERENCES semantic_memory(id) ON DELETE CASCADE,
    FOREIGN KEY (target_memory_id) REFERENCES semantic_memory(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_causal_source ON causal_links(source_memory_id);
CREATE INDEX IF NOT EXISTS idx_causal_target ON causal_links(target_memory_id);

-- 6. 审计日志表
CREATE TABLE IF NOT EXISTS memory_audit_log (
    id TEXT PRIMARY KEY,
    memory_id TEXT NOT NULL,
    operation TEXT NOT NULL,
    operator_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    details TEXT,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_memory ON memory_audit_log(memory_id);
CREATE INDEX IF NOT EXISTS idx_audit_operator ON memory_audit_log(operator_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON memory_audit_log(timestamp DESC);