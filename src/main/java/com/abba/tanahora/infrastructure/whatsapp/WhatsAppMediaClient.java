package com.abba.tanahora.infrastructure.whatsapp;

import com.abba.tanahora.infrastructure.config.WhatsAppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class WhatsAppMediaClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMediaClient.class);
    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v19.0";

    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient client = new OkHttpClient();

    public WhatsAppMediaClient(WhatsAppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public MediaPayload downloadByMediaId(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) {
            throw new IllegalArgumentException("mediaId cannot be blank");
        }

        MediaMetadata metadata = fetchMetadata(mediaId);
        Request request = new Request.Builder()
                .url(metadata.url())
                .get()
                .addHeader("Authorization", "Bearer " + properties.getAccessToken())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("Failed to download media. status=" + response.code());
            }
            return new MediaPayload(response.body().bytes(), metadata.mimeType(), metadata.filename());
        } catch (IOException e) {
            log.error("Error downloading media {}", mediaId, e);
            throw new IllegalStateException("Failed to download media", e);
        }
    }

    private MediaMetadata fetchMetadata(String mediaId) {
        Request request = new Request.Builder()
                .url(GRAPH_API_BASE + "/" + mediaId)
                .get()
                .addHeader("Authorization", "Bearer " + properties.getAccessToken())
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Failed to fetch media metadata. status=" + response.code());
            }
            JsonNode root = objectMapper.readTree(body);
            String url = root.path("url").asText(null);
            String mimeType = root.path("mime_type").asText(null);
            String filename = root.path("filename").asText(null);
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("Media URL not found for mediaId=" + mediaId);
            }
            return new MediaMetadata(url, mimeType, filename);
        } catch (IOException e) {
            log.error("Error fetching media metadata {}", mediaId, e);
            throw new IllegalStateException("Failed to fetch media metadata", e);
        }
    }

    private record MediaMetadata(String url, String mimeType, String filename) {
    }

    public record MediaPayload(byte[] bytes, String mimeType, String filename) {
    }
}
