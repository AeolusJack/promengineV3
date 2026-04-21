package com.thirdexploration.promengine.memory.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Aeon 记忆模块数据源配置。
 * 仅保留统一的 memoryDataSource，移除旧热存储数据源。
 */
@Configuration
public class MemoryConfiguration {

    @Value("${promengine.data-dir:./data}")
    private String dataDir;

    /**
     * Aeon 统一数据源（SQLite）。
     */
    @Bean
    @Primary
    public DataSource memoryDataSource() {
        HikariConfig config = new HikariConfig();
        Path dbPath = Paths.get(dataDir, "memory", "aeon_memory.db").toAbsolutePath();
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create database directory: " + dbPath.getParent(), e);
        }
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setPoolName("AeonMemoryPool");
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate memoryJdbcTemplate(DataSource memoryDataSource) {
        return new JdbcTemplate(memoryDataSource);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource memoryDataSource) {
        return new DataSourceTransactionManager(memoryDataSource);
    }
}