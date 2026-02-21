package com.abba.tanahora.application.service;

import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.SelectableBillingGateway;
import com.abba.tanahora.infrastructure.config.BillingProperties;
import com.abba.tanahora.infrastructure.config.MercadoPagoProperties;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.client.preapproval.PreapprovalCreateRequest;
import com.mercadopago.client.preapproval.PreapprovalUpdateRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preapproval.Preapproval;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MercadoPagoBillingGateway implements SelectableBillingGateway {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoBillingGateway.class);

    private final MercadoPagoProperties mercadoPagoProperties;
    private final BillingProperties billingProperties;

    @Override
    public SubscriptionData createRecurringSubscription(User user,
                                                        BigDecimal amount,
                                                        String currency,
                                                        int intervalMonths,
                                                        OffsetDateTime checkoutExpiresAt) {
        ensureTokenConfigured();
        configureSdk();
        try {
            OffsetDateTime startDate = OffsetDateTime.now().plusMinutes(5);
            PreApprovalAutoRecurringCreateRequest autoRecurring = PreApprovalAutoRecurringCreateRequest.builder()
                    .frequency(Math.max(1, intervalMonths))
                    .frequencyType("months")
                    .transactionAmount(amount)
                    .currencyId(currency)
                    .startDate(startDate)
                    .build();

            PreapprovalCreateRequest request = PreapprovalCreateRequest.builder()
                    .reason("TaNaHora Premium")
                    .externalReference(user.getWhatsappId())
                    .backUrl(billingProperties.getCheckoutBackUrl())
                    .payerEmail(user.getEmail())
                    .status("pending")
                    .autoRecurring(autoRecurring)
                    .build();

            Preapproval preapproval = new PreapprovalClient().create(request);
            return new SubscriptionData(
                    preapproval.getId(),
                    preapproval.getStatus(),
                    firstNonBlank(preapproval.getInitPoint(), preapproval.getSandboxInitPoint()),
                    preapproval.getExternalReference(),
                    preapproval.getNextPaymentDate(),
                    null
            );
        } catch (MPApiException e) {
            logApiException("create recurring subscription", e);
            throw new IllegalStateException("Failed to create Mercado Pago subscription", e);
        } catch (MPException e) {
            log.warn("Mercado Pago SDK error while creating recurring subscription: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to create Mercado Pago subscription", e);
        }
    }

    @Override
    public SubscriptionData getSubscription(String subscriptionId) {
        ensureTokenConfigured();
        configureSdk();
        try {
            Preapproval preapproval = new PreapprovalClient().get(subscriptionId);
            return new SubscriptionData(
                    preapproval.getId(),
                    preapproval.getStatus(),
                    firstNonBlank(preapproval.getInitPoint(), preapproval.getSandboxInitPoint()),
                    preapproval.getExternalReference(),
                    preapproval.getNextPaymentDate(),
                    null
            );
        } catch (MPApiException e) {
            logApiException("fetch subscription " + subscriptionId, e);
            throw new IllegalStateException("Failed to fetch Mercado Pago subscription: " + subscriptionId, e);
        } catch (MPException e) {
            log.warn("Mercado Pago SDK error while fetching subscription {}: {}", subscriptionId, e.getMessage(), e);
            throw new IllegalStateException("Failed to fetch Mercado Pago subscription: " + subscriptionId, e);
        }
    }

    @Override
    public PaymentData getPayment(String paymentId) {
        ensureTokenConfigured();
        configureSdk();
        try {
            Long id = Long.valueOf(paymentId);
            Payment payment = new PaymentClient().get(id);
            Map metadata = payment.getMetadata();
            String subscriptionId = firstNonBlank(
                    stringFromMetadata(metadata, "subscription_id"),
                    stringFromMetadata(metadata, "preapproval_id")
            );
            return new PaymentData(
                    payment.getId() != null ? payment.getId().toString() : paymentId,
                    payment.getStatus(),
                    subscriptionId,
                    payment.getExternalReference(),
                    payment.getDateApproved(),
                    null
            );
        } catch (MPApiException e) {
            logApiException("fetch payment " + paymentId, e);
            throw new IllegalStateException("Failed to fetch Mercado Pago payment: " + paymentId, e);
        } catch (MPException e) {
            log.warn("Mercado Pago SDK error while fetching payment {}: {}", paymentId, e.getMessage(), e);
            throw new IllegalStateException("Failed to fetch Mercado Pago payment: " + paymentId, e);
        }
    }

    @Override
    public SubscriptionData cancelSubscription(String subscriptionId) {
        ensureTokenConfigured();
        configureSdk();
        try {
            PreapprovalUpdateRequest request = PreapprovalUpdateRequest.builder()
                    .status("cancelled")
                    .build();
            Preapproval preapproval = new PreapprovalClient().update(subscriptionId, request);
            return new SubscriptionData(
                    preapproval.getId(),
                    preapproval.getStatus(),
                    firstNonBlank(preapproval.getInitPoint(), preapproval.getSandboxInitPoint()),
                    preapproval.getExternalReference(),
                    preapproval.getNextPaymentDate(),
                    null
            );
        } catch (MPApiException e) {
            logApiException("cancel subscription " + subscriptionId, e);
            throw new IllegalStateException("Failed to cancel Mercado Pago subscription: " + subscriptionId, e);
        } catch (MPException e) {
            log.warn("Mercado Pago SDK error while canceling subscription {}: {}", subscriptionId, e.getMessage(), e);
            throw new IllegalStateException("Failed to cancel Mercado Pago subscription: " + subscriptionId, e);
        }
    }

    @Override
    public String gatewayCode() {
        return "MERCADO_PAGO";
    }

    @Override
    public String key() {
        return "mercadopago";
    }

    private void ensureTokenConfigured() {
        if (mercadoPagoProperties.getAccessToken() == null || mercadoPagoProperties.getAccessToken().isBlank()) {
            throw new IllegalStateException("Mercado Pago access token is not configured");
        }
    }

    private void configureSdk() {
        MercadoPagoConfig.setAccessToken(mercadoPagoProperties.getAccessToken());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stringFromMetadata(Map metadata, String key) {
        if (metadata == null || key == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : value.toString();
    }

    private void logApiException(String action, MPApiException exception) {
        if (exception == null) {
            return;
        }
        var response = exception.getApiResponse();
        if (response == null) {
            log.warn("Mercado Pago API error while {}: {}", action, exception.getMessage(), exception);
            return;
        }
        log.warn("Mercado Pago API error while {}: status={} body={}",
                action,
                response.getStatusCode(),
                response.getContent(),
                exception);
    }
}
