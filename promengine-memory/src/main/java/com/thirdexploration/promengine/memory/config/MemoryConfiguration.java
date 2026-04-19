package com.thirdexploration.promengine.memory.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 记忆模块 Spring 配置。
 */
@Configuration
public class MemoryConfiguration {

    @Value("${promengine.data-dir:./data}")
    private String dataDir;

    @Bean
    public DataSource hotDataSource() {
        HikariConfig config = new HikariConfig();
        Path dbPath = Paths.get(dataDir, "memory", "hot.db");
        dbPath.getParent().toFile().mkdirs();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setPoolName("HotStoragePool");
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate hotJdbcTemplate(DataSource hotDataSource) {
        return new JdbcTemplate(hotDataSource);
    }

    @Bean
    public DataSourceInitializer hotStorageInitializer(DataSource hotDataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(hotDataSource);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema-hot.sql"));
        initializer.setDatabasePopulator(populator);
        return initializer;
    }
}