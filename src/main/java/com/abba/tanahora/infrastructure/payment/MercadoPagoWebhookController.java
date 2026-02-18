package com.abba.tanahora.infrastructure.payment;

import com.abba.tanahora.domain.service.SubscriptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@RequestMapping("/webhooks/mercadopago")
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    public MercadoPagoWebhookController(SubscriptionService subscriptionService, ObjectMapper objectMapper) {
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(@RequestHeader Map<String, String> headers,
                                        @RequestBody(required = false) String body) {
        if (body == null || body.isBlank()) {
            return ResponseEntity.ok().build();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String eventType = firstNonBlank(
                    root.path("type").asText(null),
                    root.path("topic").asText(null),
                    root.path("action").asText(null)
            );
            String resourceId = firstNonBlank(
                    root.path("data").path("id").asText(null),
                    root.path("id").asText(null)
            );
            String eventKey = firstNonBlank(
                    root.path("id").asText(null),
                    headers.get("x-request-id"),
                    sha256(body)
            );

            subscriptionService.handleMercadoPagoWebhook(eventKey, eventType, resourceId, body);
        } catch (Exception e) {
            log.error("Failed to process Mercado Pago webhook: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
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
}
