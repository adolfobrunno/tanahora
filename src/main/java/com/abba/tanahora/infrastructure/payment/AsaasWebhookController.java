package com.abba.tanahora.infrastructure.payment;

import com.abba.tanahora.domain.service.SubscriptionService;
import com.abba.tanahora.infrastructure.config.AsaasProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/webhooks/asaas")
@RequiredArgsConstructor
public class AsaasWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AsaasWebhookController.class);

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final AsaasProperties asaasProperties;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(@RequestHeader Map<String, String> headers,
                                        @RequestHeader(value = "asaas-access-token", required = false) String accessToken,
                                        @RequestBody(required = false) String body) {
        if (isTokenMismatch(accessToken)) {
            log.warn("Ignoring Asaas webhook due to invalid access token");
            return ResponseEntity.status(401).build();
        }
        if (body == null || body.isBlank()) {
            return ResponseEntity.ok().build();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String eventType = firstNonBlank(
                    root.path("event").asText(null),
                    root.path("type").asText(null)
            );
            String resourceId = resolveResourceId(root, eventType);
            String eventKey = firstNonBlank(
                    root.path("id").asText(null),
                    headers.get("x-request-id"),
                    headers.get("x-event-id"),
                    sha256(body)
            );

            subscriptionService.handleAsaasWebhook(eventKey, eventType, resourceId, body);
        } catch (Exception e) {
            log.error("Failed to process Asaas webhook: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }

    private String resolveResourceId(JsonNode root, String eventType) {
        String normalized = eventType == null ? "" : eventType.toLowerCase();
        if (normalized.contains("subscription")) {
            return firstNonBlank(
                    root.path("subscription").path("id").asText(null),
                    root.path("payment").path("subscription").asText(null),
                    root.path("id").asText(null),
                    root.path("payment").path("id").asText(null)
            );
        }
        if (normalized.contains("payment")) {
            return firstNonBlank(
                    root.path("payment").path("id").asText(null),
                    root.path("id").asText(null),
                    root.path("payment").path("subscription").asText(null)
            );
        }
        return firstNonBlank(
                root.path("subscription").path("id").asText(null),
                root.path("payment").path("id").asText(null),
                root.path("id").asText(null)
        );
    }

    private boolean isTokenMismatch(String accessToken) {
        String configuredToken = trimToNull(asaasProperties.getWebhookToken());
        if (configuredToken == null) {
            return false;
        }
        return !configuredToken.equals(trimToNull(accessToken));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "hash_error";
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
