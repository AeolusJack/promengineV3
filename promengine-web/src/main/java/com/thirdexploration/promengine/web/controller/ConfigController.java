package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.core.ConfigManagementService;
import com.thirdexploration.promengine.core.domain.ConfigFieldMeta;
import com.thirdexploration.promengine.core.domain.ConfigUpdateResult;
import com.thirdexploration.promengine.core.domain.UserConfigView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigManagementService configService;

    @GetMapping
    public UserConfigView getConfig(@RequestParam String userId) {
        return configService.getUserConfig(userId);
    }

    @GetMapping("/metadata")
    public List<ConfigFieldMeta> getMetadata() {
        return configService.getConfigMetadata();
    }

    @PatchMapping
    public ConfigUpdateResult updateConfig(@RequestParam String userId, @RequestBody Map<String, Object> updates) {
        return configService.updateConfig(userId, updates);
    }
}