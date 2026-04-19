package com.thirdexploration.promengine.cognition;

import com.thirdexploration.promengine.cognition.config.CognitionProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class CognitiveFuelManager {

    private final CognitionProperties properties;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger currentFuel = new AtomicInteger(100);
    private volatile Instant lastActivity = Instant.now();
    private final AtomicInteger dailyBoostUsed = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        currentFuel.set(properties.getCarbonFuel().getMaxFuel());
        meterRegistry.gauge("promengine.carbon.fuel.current", currentFuel);
    }

    public int getCurrentFuel() {
        recoverNaturally();
        return currentFuel.get();
    }

    public void consume(int amount) {
        lastActivity = Instant.now();
        currentFuel.updateAndGet(f -> Math.max(0, f - amount));
        log.debug("Fuel consumed: {}, remaining: {}", amount, currentFuel.get());
    }

    public void boost(int amount) {
        CognitionProperties.CarbonFuelConfig fuelConfig = properties.getCarbonFuel();
        if (dailyBoostUsed.get() >= fuelConfig.getBoostLimitPerDay()) {
            log.warn("Daily boost limit reached");
            return;
        }
        currentFuel.updateAndGet(f -> Math.min(fuelConfig.getMaxFuel(), f + amount));
        dailyBoostUsed.incrementAndGet();
        log.info("Fuel boosted by {}, current: {}", amount, currentFuel.get());
    }

    @Scheduled(fixedRate = 3600000) // 每小时
    public void recoverNaturally() {
        CognitionProperties.CarbonFuelConfig fuelConfig = properties.getCarbonFuel();
        int recovery = fuelConfig.getRecoveryPerHour();
        currentFuel.updateAndGet(f -> Math.min(fuelConfig.getMaxFuel(), f + recovery));
    }

    @Scheduled(cron = "0 0 0 * * ?") // 每日重置
    public void resetDailyBoost() {
        dailyBoostUsed.set(0);
    }
}