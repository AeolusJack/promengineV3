package com.thirdexploration.promengine.ecosystem.feishu;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.ecosystem.platforms.feishu")
public class FeishuProperties {
    private boolean enabled = false;
    private String apiEndpoint = "https://open.feishu.cn";
    private String appId;
    private String appSecret;
    private String appAccessToken;
}