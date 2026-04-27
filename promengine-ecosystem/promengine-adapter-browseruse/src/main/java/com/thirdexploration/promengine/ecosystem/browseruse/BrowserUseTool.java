package com.thirdexploration.promengine.ecosystem.browseruse;

import com.thirdexploration.promengine.executor.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrowserUseTool implements Tool {

    private final BrowserUseProperties properties;

    @Override
    public String getName() {
        return "browser";
    }

    @Override
    public String getDescription() {
        return "自动化浏览器操作";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params) {
        if (!properties.isEnabled()) {
            return Map.of("error", "Browser automation is disabled");
        }
        String action = (String) params.get("action");
        String url = (String) params.get("url");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get(url);
            String title = driver.getTitle();
            return Map.of("title", title, "url", driver.getCurrentUrl());
        } finally {
            driver.quit();
        }
    }
}