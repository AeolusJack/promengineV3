package com.thirdexploration.promengine.memory.config;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class Neo4jConfig {

    @Value("${aeon.memory.graph.uri:bolt://localhost:7687}")
    private String uri;

    @Value("${aeon.memory.graph.username:neo4j}")
    private String username;

    @Value("${aeon.memory.graph.password:neo4jneo4j}")
    private String password;

    @Bean
    @ConditionalOnProperty(name = "aeon.memory.graph.enabled", havingValue = "true")
    public Driver neo4jDriver() {
        try {
            Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
            // 验证连接是否有效（5秒超时）
            driver.verifyConnectivityAsync().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            log.info("Neo4j connection established successfully at {}", uri);
            return driver;
        } catch (Exception e) {
            log.error("Failed to connect to Neo4j at {}: {}. Graph features will be disabled.", uri, e.getMessage());
            // 返回 null，后续 Bean 会被条件注解跳过或通过其他方式处理
            return null;
        }
    }
}