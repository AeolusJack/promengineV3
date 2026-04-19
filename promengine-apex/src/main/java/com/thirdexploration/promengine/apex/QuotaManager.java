package com.thirdexploration.promengine.apex;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thirdexploration.promengine.core.exception.QuotaExceededException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaManager {

    private final ApexProperties properties;
    private final Map<String, UserQuota> userQuotas = new ConcurrentHashMap<>();
    private final Cache<String, Boolean> reservationCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    @PostConstruct
    public void init() {
        log.info("QuotaManager initialized with daily budget: ${}, monthly: ${}",
                properties.getBudget().getDaily(), properties.getBudget().getMonthly());
    }

    public boolean checkAndReserve(String userId, long estimatedTokens) {
        UserQuota quota = userQuotas.computeIfAbsent(userId, k -> new UserQuota());
        double estimatedCost = estimatedTokens * 0.001 / 1000; // 简化估算
        if (quota.getDailyUsed() + estimatedCost > properties.getBudget().getDaily()) {
            dispatchAlertIfNeeded(userId, quota);
            return false;
        }
        reservationCache.put(userId + "_" + System.nanoTime(), true);
        return true;
    }

    public void deduct(String userId, long actualTokens) {
        UserQuota quota = userQuotas.get(userId);
        if (quota == null) return;
        double cost = actualTokens * 0.001 / 1000;
        quota.addUsage(cost);
    }

    private void dispatchAlertIfNeeded(String userId, UserQuota quota) {
        double usageRatio = quota.getDailyUsed() / properties.getBudget().getDaily();
        if (usageRatio >= 0.95) {
            log.warn("User {} daily quota at {}%", userId, usageRatio * 100);
        }
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void resetDaily() {
        userQuotas.values().forEach(UserQuota::resetDaily);
        log.info("Daily quotas reset");
    }

    private static class UserQuota {
        private final AtomicLong dailyUsedCents = new AtomicLong(0);
        private final AtomicLong monthlyUsedCents = new AtomicLong(0);
        private LocalDate lastReset = LocalDate.now();

        void addUsage(double cost) {
            dailyUsedCents.addAndGet((long) (cost * 100));
            monthlyUsedCents.addAndGet((long) (cost * 100));
        }

        double getDailyUsed() {
            return dailyUsedCents.get() / 100.0;
        }

        void resetDaily() {
            dailyUsedCents.set(0);
        }
    }
}