package com.abba.tanahora.application.service;

import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.BillingGateway;
import com.abba.tanahora.domain.service.SelectableBillingGateway;
import com.abba.tanahora.infrastructure.config.BillingProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Primary
public class RoutingBillingGateway implements BillingGateway {

    private final Map<String, SelectableBillingGateway> gatewaysByKey;
    private final BillingProperties billingProperties;

    public RoutingBillingGateway(List<SelectableBillingGateway> gateways, BillingProperties billingProperties) {
        this.gatewaysByKey = gateways.stream()
                .collect(Collectors.toMap(gateway -> normalize(gateway.key()), Function.identity()));
        this.billingProperties = billingProperties;
    }

    @Override
    public String gatewayCode() {
        return selectedGateway().gatewayCode();
    }

    @Override
    public SubscriptionData createRecurringSubscription(User user, BigDecimal amount, String currency, int intervalMonths, OffsetDateTime checkoutExpiresAt) {
        return selectedGateway().createRecurringSubscription(user, amount, currency, intervalMonths, checkoutExpiresAt);
    }

    @Override
    public SubscriptionData getSubscription(String subscriptionId) {
        return selectedGateway().getSubscription(subscriptionId);
    }

    @Override
    public PaymentData getPayment(String paymentId) {
        return selectedGateway().getPayment(paymentId);
    }

    @Override
    public SubscriptionData cancelSubscription(String subscriptionId) {
        return selectedGateway().cancelSubscription(subscriptionId);
    }

    private SelectableBillingGateway selectedGateway() {
        String selectedKey = normalize(billingProperties.getGateway());
        SelectableBillingGateway gateway = gatewaysByKey.get(selectedKey);
        if (gateway == null) {
            throw new IllegalStateException("Unsupported billing gateway '" + billingProperties.getGateway()
                    + "'. Available: " + String.join(", ", gatewaysByKey.keySet()));
        }
        return gateway;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
