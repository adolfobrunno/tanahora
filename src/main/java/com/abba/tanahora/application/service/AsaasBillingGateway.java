package com.abba.tanahora.application.service;

import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.SelectableBillingGateway;
import com.abba.tanahora.infrastructure.config.AsaasProperties;
import com.asaas.apisdk.AsaasSdk;
import com.asaas.apisdk.exceptions.ApiError;
import com.asaas.apisdk.models.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AsaasBillingGateway implements SelectableBillingGateway {

    private static final Logger log = LoggerFactory.getLogger(AsaasBillingGateway.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AsaasProperties asaasProperties;

    @Override
    public String gatewayCode() {
        return "ASAAS";
    }

    @Override
    public String key() {
        return "asaas";
    }

    @Override
    public SubscriptionData createRecurringSubscription(User user,
                                                        BigDecimal amount,
                                                        String currency,
                                                        int intervalMonths,
                                                        OffsetDateTime checkoutExpiresAt) {
        validateConfigured();
        try {
            AsaasSdk sdk = createSdk();
            PaymentLinkSaveRequestDto paymentLinkRequest = PaymentLinkSaveRequestDto.builder()
                    .billingType(resolvePaymentLinkBillingType())
                    .name("TaNaHora Premium")
                    .value(amount.setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .externalReference(user.getWhatsappId())
                    .chargeType(PaymentLinkSaveRequestChargeType.RECURRENT)
                    .subscriptionCycle(resolvePaymentLinkCycle(intervalMonths))
                    .description("TaNaHora Premium")
                    .dueDateLimitDays(1L)
                    .build();

            PaymentLinkGetResponseDto paymentLink = sdk.paymentLink.createAPaymentsLink(paymentLinkRequest);
            return new SubscriptionData(
                    null,
                    "pending",
                    paymentLink.getUrl(),
                    firstNonBlank(paymentLink.getExternalReference(), user.getWhatsappId()),
                    OffsetDateTime.now().plusMonths(Math.max(1, intervalMonths)),
                    paymentLink.getId()
            );
        } catch (ApiError e) {
            log.warn("Asaas create payment link failed code={} message={}", e.getStatus(), e.getMessage());
            throw new IllegalStateException("Failed to create Asaas recurring payment link", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Asaas recurring payment link", e);
        }
    }

    @Override
    public SubscriptionData getSubscription(String subscriptionId) {
        validateConfigured();
        try {
            AsaasSdk sdk = createSdk();
            SubscriptionGetResponseDto subscription = sdk.subscription.retrieveASingleSubscription(subscriptionId);
            String checkoutUrl = resolveCheckoutUrl(sdk, subscriptionId);
            return new SubscriptionData(
                    subscription.getId(),
                    valueOf(subscription.getStatus()),
                    firstNonBlank(checkoutUrl, subscription.getPaymentLink()),
                    subscription.getExternalReference(),
                    parseOffsetDateTime(subscription.getNextDueDate()),
                    subscription.getPaymentLink()
            );
        } catch (ApiError e) {
            if (e.getStatus() == 404) {
                try {
                    AsaasSdk sdk = createSdk();
                    PaymentLinkGetResponseDto paymentLink = sdk.paymentLink.retrieveASinglePaymentsLink(subscriptionId);
                    String status = Boolean.TRUE.equals(paymentLink.getDeleted()) || Boolean.FALSE.equals(paymentLink.getActive())
                            ? "canceled"
                            : "pending";
                    return new SubscriptionData(
                            subscriptionId,
                            status,
                            paymentLink.getUrl(),
                            paymentLink.getExternalReference(),
                            null,
                            paymentLink.getId()
                    );
                } catch (ApiError fallback) {
                    log.warn("Asaas get payment link failed id={} code={} message={}",
                            subscriptionId, fallback.getStatus(), fallback.getMessage());
                } catch (Exception fallback) {
                    log.warn("Asaas get payment link failed id={} reason={}", subscriptionId, fallback.getMessage());
                }
            }
            log.warn("Asaas get subscription failed id={} code={} message={}", subscriptionId, e.getStatus(), e.getMessage());
            throw new IllegalStateException("Failed to fetch Asaas subscription/paymentLink: " + subscriptionId, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch Asaas subscription/paymentLink: " + subscriptionId, e);
        }
    }

    @Override
    public PaymentData getPayment(String paymentId) {
        validateConfigured();
        try {
            AsaasSdk sdk = createSdk();
            PaymentGetResponseDto payment = sdk.payment.retrieveASinglePayment(paymentId);
            return new PaymentData(
                    payment.getId(),
                    toInternalPaymentStatus(payment.getStatus()),
                    payment.getSubscription(),
                    payment.getExternalReference(),
                    parseOffsetDateTime(firstNonBlank(payment.getPaymentDate(), payment.getClientPaymentDate(), payment.getDateCreated())),
                    payment.getPaymentLink()
            );
        } catch (ApiError e) {
            log.warn("Asaas get payment failed id={} code={} message={}", paymentId, e.getStatus(), e.getMessage());
            throw new IllegalStateException("Failed to fetch Asaas payment: " + paymentId, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch Asaas payment: " + paymentId, e);
        }
    }

    @Override
    public SubscriptionData cancelSubscription(String subscriptionId) {
        validateConfigured();
        try {
            AsaasSdk sdk = createSdk();
            PaymentLinkDeleteResponseDto deleted = sdk.paymentLink.removeAPaymentsLink(subscriptionId);
            return new SubscriptionData(
                    deleted.getId(),
                    "cancelled",
                    null,
                    null,
                    null,
                    deleted.getId()
            );
        } catch (ApiError e) {
            if (e.getStatus() == 404) {
                try {
                    AsaasSdk sdk = createSdk();
                    SubscriptionDeleteResponseDto deleted = sdk.subscription.removeSubscription(subscriptionId);
                    return new SubscriptionData(
                            deleted.getId(),
                            "cancelled",
                            null,
                            null,
                            null,
                            null
                    );
                } catch (ApiError fallback) {
                    try {
                        AsaasSdk sdk = createSdk();
                        String resolvedSubscriptionId = resolveSubscriptionIdFromPayment(sdk, subscriptionId);
                        if (resolvedSubscriptionId != null && !resolvedSubscriptionId.equals(subscriptionId)) {
                            SubscriptionDeleteResponseDto deleted = sdk.subscription.removeSubscription(resolvedSubscriptionId);
                            return new SubscriptionData(
                                    deleted.getId(),
                                    "cancelled",
                                    null,
                                    null,
                                    null,
                                    null
                            );
                        }
                    } catch (Exception ignored) {
                    }
                    log.warn("Asaas cancel fallback failed id={} code={} message={}",
                            subscriptionId, fallback.getStatus(), fallback.getMessage());
                }
            }
            log.warn("Asaas cancel failed id={} code={} message={}", subscriptionId, e.getStatus(), e.getMessage());
            throw new IllegalStateException("Failed to cancel Asaas recurring resource: " + subscriptionId, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to cancel Asaas recurring resource: " + subscriptionId, e);
        }
    }

    private AsaasSdk createSdk() {
        AsaasSdk sdk = new AsaasSdk();
        sdk.setApiKey(asaasProperties.getApiKey());
        sdk.setBaseUrl(asaasProperties.getApiBaseUrl());
        return sdk;
    }

    private void validateConfigured() {
        if (asaasProperties.getApiKey() == null || asaasProperties.getApiKey().isBlank()) {
            throw new IllegalStateException("Asaas api key is not configured");
        }
    }

    private String resolveCheckoutUrl(AsaasSdk sdk, String id) throws ApiError {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            PaymentListResponseDto paymentList = sdk.subscription.listPaymentsOfASubscription(
                    id,
                    ListPaymentsOfASubscriptionParameters.builder().build()
            );
            if (paymentList != null && paymentList.getData() != null && !paymentList.getData().isEmpty()) {
                PaymentGetResponseDto firstPayment = paymentList.getData().getFirst();
                return firstNonBlank(firstPayment.getInvoiceUrl(), firstPayment.getBankSlipUrl(), firstPayment.getPaymentLink());
            }
        } catch (ApiError e) {
            if (e.getStatus() != 404) {
                throw e;
            }
        }

        PaymentLinkGetResponseDto paymentLink = sdk.paymentLink.retrieveASinglePaymentsLink(id);
        return paymentLink.getUrl();
    }

    private String resolveSubscriptionIdFromPayment(AsaasSdk sdk, String candidatePaymentId) {
        if (candidatePaymentId == null || candidatePaymentId.isBlank()) {
            return null;
        }
        try {
            PaymentGetResponseDto payment = sdk.payment.retrieveASinglePayment(candidatePaymentId);
            return trimToNull(payment.getSubscription());
        } catch (Exception ignored) {
            return null;
        }
    }

    private PaymentLinkSaveRequestBillingType resolvePaymentLinkBillingType() {
        String raw = trimToNull(asaasProperties.getBillingType());
        if (raw == null) {
            return PaymentLinkSaveRequestBillingType.UNDEFINED;
        }
        try {
            return PaymentLinkSaveRequestBillingType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return PaymentLinkSaveRequestBillingType.UNDEFINED;
        }
    }

    private PaymentLinkSaveRequestCycle resolvePaymentLinkCycle(int intervalMonths) {
        return switch (Math.max(1, intervalMonths)) {
            case 1 -> PaymentLinkSaveRequestCycle.MONTHLY;
            case 2 -> PaymentLinkSaveRequestCycle.BIMONTHLY;
            case 3 -> PaymentLinkSaveRequestCycle.QUARTERLY;
            case 6 -> PaymentLinkSaveRequestCycle.SEMIANNUALLY;
            case 12 -> PaymentLinkSaveRequestCycle.YEARLY;
            default -> PaymentLinkSaveRequestCycle.MONTHLY;
        };
    }

    private String toInternalPaymentStatus(PaymentGetResponsePaymentStatus paymentStatus) {
        if (paymentStatus == null) {
            return null;
        }
        return switch (paymentStatus) {
            case RECEIVED, CONFIRMED, RECEIVED_IN_CASH -> "approved";
            default -> paymentStatus.getValue();
        };
    }

    private String valueOf(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDate date = LocalDate.parse(value, DATE_FORMATTER);
            return date.atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(value);
            return dateTime.atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
