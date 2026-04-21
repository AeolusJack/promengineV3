package com.thirdexploration.promengine.memory.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {

    @Value("${aeon.memory.graph.uri:bolt://localhost:7687}")
    private String uri;

    @Value("${aeon.memory.graph.username:neo4j}")
    private String username;

    @Value("${aeon.memory.graph.password:neo4j}")
    private String password;

    @Bean
    public Driver neo4jDriver() {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}