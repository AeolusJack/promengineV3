package com.thirdexploration.promengine.psych;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PsychFirstAidService {

    private boolean active = false;
    private long cooldownUntil = 0;
    private int rejectCount = 0;

    public boolean detectNegativeEmotion(String text) {
        // 简单关键词检测
        String lower = text.toLowerCase();
        return lower.contains("难过") || lower.contains("伤心") || lower.contains("焦虑");
    }

    public boolean shouldOfferSupport() {
        if (active) return false;
        if (System.currentTimeMillis() < cooldownUntil) return false;
        if (rejectCount >= 3) return false;
        return true;
    }

    public void activate() {
        active = true;
        log.info("Psychological first aid activated");
    }

    public void deactivate() {
        active = false;
        cooldownUntil = System.currentTimeMillis() + 2 * 60 * 60 * 1000; // 2小时冷却
    }

    public void recordRejection() {
        rejectCount++;
    }

    public boolean isActive() {
        return active;
    }
}