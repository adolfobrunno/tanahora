package com.abba.tanahora.infrastructure.whatsapp;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.abba.tanahora.application.notification.WhatsAppGateway;
import com.abba.tanahora.application.notification.WhatsAppMessage;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.infrastructure.config.WhatsAppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.*;

@Service
@RequiredArgsConstructor
public class WhatsAppGatewayImpl implements WhatsAppGateway {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppGatewayImpl.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v19.0";

    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    public String sendMessage(User user, WhatsAppMessage message) {

        if (message == null) {
            log.warn("Missing WhatsApp message");
            return "";
        }

        String payload = message.buildPayload();
        if (payload == null || payload.isBlank()) {
            log.warn("Unable to build WhatsApp payload for type={}", message.getType());
            return "";
        }

        return sendPayload(user, payload);
    }

    private String sendPayload(User user, String payload) {
        Request request = new Request.Builder()
                .url(GRAPH_API_BASE + "/" + properties.getFromNumber().trim() + "/messages")
                .post(RequestBody.create(payload, JSON))
                .addHeader("Authorization", "Bearer " + properties.getAccessToken())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.warn("WhatsApp send failed status={} body={}", response.code(), responseBody);
                return "";
            }
            String messageId = extractMessageId(responseBody);
            log.info("WhatsApp message sent to={} id={}", mask(user.getWhatsappId()), messageId);
            return messageId;
        } catch (IOException e) {
            log.error("Failed to send WhatsApp message: {}", e.getMessage(), e);
            return "";
        }
    }


    private String extractMessageId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode messages = root.path("messages");
            if (messages.isArray() && !messages.isEmpty()) {
                return messages.get(0).path("id").asText("");
            }
        } catch (Exception e) {
            log.warn("Failed to parse WhatsApp response id: {}", e.getMessage());
        }
        return "";
    }

    private String mask(String v) {
        if (v == null || v.isBlank()) return "";
        if (v.length() <= 6) return "***";
        return v.substring(0, 3) + "***" + v.substring(v.length() - 3);
    }
}
