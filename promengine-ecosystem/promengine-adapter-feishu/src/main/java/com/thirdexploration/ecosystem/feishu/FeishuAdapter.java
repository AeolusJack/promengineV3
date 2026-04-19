package com.thirdexploration.ecosystem.feishu;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuAdapter {

    private final FeishuProperties properties;
    private final WebClient webClient = WebClient.create();

    public void sendMessage(String receiveId, String content) {
        if (!properties.isEnabled()) return;
        String url = properties.getApiEndpoint() + "/open-apis/im/v1/messages?receive_id_type=open_id";
        webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + properties.getAppAccessToken())
                .bodyValue(Map.of("receive_id", receiveId, "content", content, "msg_type", "text"))
                .retrieve()
                .toBodilessEntity()
                .subscribe();
    }
}