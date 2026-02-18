package com.abba.tanahora.infrastructure.scheduler;

import com.abba.tanahora.domain.service.SubscriptionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "tictacmed.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionReconciliationJob {

    private final SubscriptionService subscriptionService;

    public SubscriptionReconciliationJob(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Scheduled(cron = "${tanahora.billing.reconciliation-cron:0 0 3 * * *}")
    public void reconcileDaily() {
        subscriptionService.runDailyReconciliation();
    }
}
