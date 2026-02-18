package com.abba.tanahora.domain.service;

import com.abba.tanahora.application.dto.DowngradeResult;
import com.abba.tanahora.application.dto.PlanInfoResult;
import com.abba.tanahora.application.dto.UpgradeCheckoutResult;

public interface SubscriptionService {

    UpgradeCheckoutResult createOrReuseUpgradeLink(String whatsappId, String contactName);

    DowngradeResult requestDowngrade(String whatsappId, String contactName);

    PlanInfoResult getPlanInfo(String whatsappId, String contactName);

    void handleMercadoPagoWebhook(String eventKey, String eventType, String resourceId, String payload);

    void runDailyReconciliation();
}
