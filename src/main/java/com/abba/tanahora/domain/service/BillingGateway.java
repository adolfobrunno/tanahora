package com.abba.tanahora.domain.service;

import com.abba.tanahora.domain.model.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface BillingGateway {

    default String gatewayCode() {
        return "UNKNOWN";
    }

    SubscriptionData createRecurringSubscription(
            User user,
            BigDecimal amount,
            String currency,
            int intervalMonths,
            OffsetDateTime checkoutExpiresAt
    );

    SubscriptionData getSubscription(String subscriptionId);

    PaymentData getPayment(String paymentId);

    SubscriptionData cancelSubscription(String subscriptionId);

    record SubscriptionData(
            String id,
            String status,
            String checkoutUrl,
            String externalReference,
            OffsetDateTime nextPaymentDate,
            String paymentLinkId
    ) {
    }

    record PaymentData(
            String id,
            String status,
            String subscriptionId,
            String externalReference,
            OffsetDateTime approvedAt,
            String paymentLinkId
    ) {
    }
}
