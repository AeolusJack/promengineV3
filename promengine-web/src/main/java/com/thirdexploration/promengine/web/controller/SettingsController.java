package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.core.ConfigManagementService;
import com.thirdexploration.promengine.core.domain.UserConfigView;
import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final ConfigManagementService configService;

    /**
     * 获取当前用户配置
     */
    @GetMapping
    public ApiResponse<UserConfigView> getSettings(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.ok(configService.getUserConfig(userId));
    }

    /**
     * 保存配置（全量或部分更新）
     */
    @PutMapping
    public ApiResponse<Object> saveSettings(@RequestHeader("X-User-Id") String userId,
                                            @RequestBody Map<String, Object> updates) {
        var result = configService.updateConfig(userId, updates);
        if (result.isSuccess()) {
            return ApiResponse.ok(result);
        }
        return ApiResponse.error(String.join(", ", result.getValidationErrors()));
    }

    /**
     * 重置为默认配置
     */
    @PostMapping("/reset")
    public ApiResponse<Void> resetSettings(@RequestHeader("X-User-Id") String userId) {
        configService.resetToDefault(userId);
        return ApiResponse.ok(null);
    }
}