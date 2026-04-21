package com.thirdexploration.promengine.memory.config;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OllamaClientConfig {

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Bean
    public OllamaApi ollamaApi() {
        // 1. 配置 RestClient 的超时
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofMinutes(3));  // 关键：读超时 3 分钟

        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        // 2. 配置 WebClient 的超时（用于流式调用）
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(3));

        WebClient.Builder webClientBuilder = WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient));

        return new OllamaApi(baseUrl, restClientBuilder, webClientBuilder);
    }
}