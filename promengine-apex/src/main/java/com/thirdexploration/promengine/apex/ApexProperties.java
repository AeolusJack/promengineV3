package com.thirdexploration.promengine.apex;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "promengine.apex")
public class ApexProperties {
    private boolean enabled = true;
    private BudgetConfig budget = new BudgetConfig();
    private AuditConfig audit = new AuditConfig();

    @Data
    public static class BudgetConfig {
        private double daily = 5.00;
        private double monthly = 50.00;
        private List<Double> alertThresholds = List.of(0.8, 0.95);
    }

    @Data
    public static class AuditConfig {
        private boolean enabled = true;
        private String path = "./data/apex-audit";
    }
}