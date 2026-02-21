package com.abba.tanahora.application.service;

import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.SelectableBillingGateway;
import com.abba.tanahora.infrastructure.config.BillingProperties;
import com.abba.tanahora.infrastructure.config.MercadoPagoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MercadoPagoBillingGateway implements SelectableBillingGateway {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoBillingGateway.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final MercadoPagoProperties mercadoPagoProperties;
    private final BillingProperties billingProperties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    public SubscriptionData createRecurringSubscription(User user,
                                                        BigDecimal amount,
                                                        String currency,
                                                        int intervalMonths,
                                                        OffsetDateTime checkoutExpiresAt) {
        ensureTokenConfigured();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reason", "TaNaHora Premium");
            payload.put("external_reference", user.getWhatsappId());
            payload.put("back_url", billingProperties.getCheckoutBackUrl());
            payload.put("status", "pending");
            payload.put("date_of_expiration", checkoutExpiresAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            payload.put("auto_recurring", Map.of(
                    "frequency", intervalMonths,
                    "frequency_type", "months",
                    "transaction_amount", amount,
                    "currency_id", currency
            ));

            String responseBody = executeRequest("POST", "/subscriptions", objectMapper.writeValueAsString(payload));
            JsonNode root = objectMapper.readTree(responseBody);
            return new SubscriptionData(
                    root.path("id").asText(null),
                    root.path("status").asText(null),
                    firstNonBlank(root.path("init_point").asText(null), root.path("sandbox_init_point").asText(null)),
                    root.path("external_reference").asText(user.getWhatsappId()),
                    parseOffsetDateTime(root.path("next_payment_date").asText(null)),
                    null
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create Mercado Pago subscription", e);
        }
    }

    @Override
    public SubscriptionData getSubscription(String subscriptionId) {
        ensureTokenConfigured();
        try {
            String responseBody = executeRequest("GET", "/subscriptions/" + subscriptionId, null);
            JsonNode root = objectMapper.readTree(responseBody);
            return new SubscriptionData(
                    root.path("id").asText(null),
                    root.path("status").asText(null),
                    firstNonBlank(root.path("init_point").asText(null), root.path("sandbox_init_point").asText(null)),
                    root.path("external_reference").asText(null),
                    parseOffsetDateTime(root.path("next_payment_date").asText(null)),
                    null
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch Mercado Pago subscription: " + subscriptionId, e);
        }
    }

    @Override
    public PaymentData getPayment(String paymentId) {
        ensureTokenConfigured();
        try {
            String responseBody = executeRequest("GET", "/v1/payments/" + paymentId, null);
            JsonNode root = objectMapper.readTree(responseBody);
            String subscriptionId = firstNonBlank(
                    root.path("subscription_id").asText(null),
                    root.path("preapproval_id").asText(null),
                    root.path("metadata").path("preapproval_id").asText(null),
                    root.path("metadata").path("subscription_id").asText(null)
            );
            return new PaymentData(
                    root.path("id").asText(paymentId),
                    root.path("status").asText(null),
                    subscriptionId,
                    root.path("external_reference").asText(null),
                    parseOffsetDateTime(root.path("date_approved").asText(null)),
                    null
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch Mercado Pago payment: " + paymentId, e);
        }
    }

    @Override
    public SubscriptionData cancelSubscription(String subscriptionId) {
        ensureTokenConfigured();
        try {
            Map<String, Object> payload = Map.of("status", "cancelled");
            String responseBody = executeRequest("PUT", "/subscriptions/" + subscriptionId, objectMapper.writeValueAsString(payload));
            JsonNode root = objectMapper.readTree(responseBody);
            return new SubscriptionData(
                    root.path("id").asText(subscriptionId),
                    root.path("status").asText(null),
                    firstNonBlank(root.path("init_point").asText(null), root.path("sandbox_init_point").asText(null)),
                    root.path("external_reference").asText(null),
                    parseOffsetDateTime(root.path("next_payment_date").asText(null)),
                    null
            );
        } catch (IOException e) {
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

    private String executeRequest(String method, String path, String payload) throws IOException {
        RequestBody requestBody = payload == null ? null : RequestBody.create(payload, JSON);
        Request.Builder builder = new Request.Builder()
                .url(baseUrl() + path)
                .addHeader("Authorization", "Bearer " + mercadoPagoProperties.getAccessToken())
                .addHeader("Content-Type", "application/json");
        if ("POST".equalsIgnoreCase(method)) {
            builder.post(requestBody == null ? RequestBody.create("", JSON) : requestBody);
        } else if ("GET".equalsIgnoreCase(method)) {
            builder.get();
        } else if ("PUT".equalsIgnoreCase(method)) {
            builder.put(requestBody == null ? RequestBody.create("{}", JSON) : requestBody);
        } else {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.warn("Mercado Pago request failed status={} path={} body={}", response.code(), path, responseBody);
                throw new IllegalStateException("Mercado Pago request failed with status " + response.code());
            }
            return responseBody;
        }
    }

    private void ensureTokenConfigured() {
        if (mercadoPagoProperties.getAccessToken() == null || mercadoPagoProperties.getAccessToken().isBlank()) {
            throw new IllegalStateException("Mercado Pago access token is not configured");
        }
    }

    private String baseUrl() {
        String baseUrl = mercadoPagoProperties.getApiBaseUrl();
        return (baseUrl == null || baseUrl.isBlank()) ? "https://api.mercadopago.com" : baseUrl;
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
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
}
