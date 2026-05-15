package com.thirdexploration.promengine.agent.common.init;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AgentSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public AgentSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing agent related tables...");
        List<String> ddlStatements = List.of(
            // 已在 AeonMemoryInitializer 中存在的 agents 表不重复创建，这里只处理新增表
            """
            CREATE TABLE IF NOT EXISTS agent_workflows (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT DEFAULT '',
                version TEXT DEFAULT '1.0.0',
                steps TEXT NOT NULL,
                triggers TEXT DEFAULT '{}',
                max_steps INTEGER DEFAULT 10,
                timeout_seconds INTEGER DEFAULT 600,
                fallback_strategy TEXT DEFAULT 'stop',
                created_by TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS agent_knowledge_bases (
                id TEXT PRIMARY KEY,
                agent_id TEXT NOT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                config TEXT NOT NULL,
                priority INTEGER DEFAULT 0,
                enabled INTEGER DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS agent_tool_bindings (
                id TEXT PRIMARY KEY,
                agent_id TEXT NOT NULL,
                tool_name TEXT NOT NULL,
                config TEXT DEFAULT '{}',
                enabled INTEGER DEFAULT 1,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS agent_execution_logs (
                id TEXT PRIMARY KEY,
                agent_id TEXT NOT NULL,
                session_id TEXT,
                task_id TEXT,
                step_name TEXT,
                status TEXT NOT NULL,
                input TEXT,
                output TEXT,
                error_message TEXT,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                duration_ms INTEGER,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS agent_human_reviews (
                id TEXT PRIMARY KEY,
                agent_id TEXT NOT NULL,
                session_id TEXT,
                task_id TEXT,
                request_type TEXT NOT NULL,
                request_data TEXT NOT NULL,
                response_data TEXT,
                status TEXT DEFAULT 'pending',
                created_at INTEGER NOT NULL,
                resolved_at INTEGER,
                FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS agent_templates (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT DEFAULT 'general',
                description TEXT DEFAULT '',
                template_config TEXT NOT NULL,
                created_by TEXT,
                visibility TEXT DEFAULT 'private',
                downloads INTEGER DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS agent_evaluations (
                id TEXT PRIMARY KEY,
                agent_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                rating INTEGER CHECK(rating BETWEEN 1 AND 5),
                tags TEXT,
                comment TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE
            )
            """
        );

        for (String sql : ddlStatements) {
            try {
                jdbcTemplate.execute(sql);
            } catch (DataAccessException e) {
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    log.debug("Table already exists, skipped: {}", sql.substring(0, Math.min(50, sql.length())));
                } else {
                    log.warn("Failed to execute DDL: {} - {}", sql.substring(0, Math.min(50, sql.length())), e.getMessage());
                }
            }
        }
        log.info("Agent tables initialization completed.");
    }
}