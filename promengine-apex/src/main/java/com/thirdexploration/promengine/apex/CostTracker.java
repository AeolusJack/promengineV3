package com.thirdexploration.promengine.apex;

import com.thirdexploration.promengine.core.ApexController;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class CostTracker {

    private final MeterRegistry meterRegistry;
    private final AtomicLong totalCostCents = new AtomicLong(0);

    public void record(String userId, ApexController.UsageRecord record) {
        totalCostCents.addAndGet((long) (record.getCost() * 100));
        meterRegistry.counter("promengine.api.cost.total").increment((long) (record.getCost() * 100));
        meterRegistry.counter("promengine.api.cost.user", "userId", userId).increment((long) (record.getCost() * 100));
    }

    public double getTotalCost() {
        return totalCostCents.get() / 100.0;
    }
}