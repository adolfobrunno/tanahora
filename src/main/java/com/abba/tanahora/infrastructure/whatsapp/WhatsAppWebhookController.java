package com.abba.tanahora.infrastructure.whatsapp;

import com.abba.tanahora.domain.model.MessageReceived;
import com.abba.tanahora.domain.service.MessageReceivedService;
import com.abba.tanahora.infrastructure.config.WhatsAppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppProperties props;
    private final MessageReceivedService messageReceivedService;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookController(WhatsAppProperties props,
                                     MessageReceivedService messageReceivedService,
                                     ObjectMapper objectMapper) {
        this.props = props;
        this.messageReceivedService = messageReceivedService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!props.isEnabled()) {
            log.info("[WhatsApp disabled] Received verification request mode={} token={}.", mode, mask(verifyToken));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if ("subscribe".equals(mode) && props.getVerifyToken() != null && props.getVerifyToken().equals(verifyToken)) {
            log.info("WhatsApp webhook verified successfully.");
            return ResponseEntity.ok(challenge != null ? challenge : "");
        }
        log.warn("WhatsApp webhook verification failed: mode={} token={}.", mode, mask(verifyToken));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(@RequestHeader Map<String, String> headers,
                                        @RequestBody(required = false) String body) {
        if (!props.isEnabled()) {
            log.info("[WhatsApp disabled] Received webhook POST body length={}", body == null ? 0 : body.length());
            return ResponseEntity.ok().build();
        }
        if (body == null || body.isBlank()) {
            log.warn("Empty webhook body");
            return ResponseEntity.ok().build();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("entry") && root.get("entry").isArray()) {
                for (JsonNode entry : root.get("entry")) {
                    JsonNode changes = entry.path("changes");
                    if (!changes.isArray()) continue;
                    for (JsonNode change : changes) {
                        JsonNode value = change.path("value");

                        String contactName = null;
                        JsonNode contacts = value.path("contacts");
                        if (contacts.isArray() && !contacts.isEmpty()) {
                            contactName = contacts.get(0).path("profile").path("name").asText(null);
                        }

                        JsonNode messages = value.path("messages");
                        if (!messages.isArray()) continue;
                        for (JsonNode msg : messages) {
                            String messageId = text(msg, "id");
                            String from = text(msg, "from");
                            String replyToMessageId = msg.path("context").path("id").asText(null);
                            String type = msg.path("type").asText(null);

                            String bodyText = null;
                            String interactiveButtonId = null;
                            String mediaId = null;
                            String mediaMimeType = null;
                            String mediaFilename = null;
                            String mediaSha256 = null;

                            if ("interactive".equals(type)) {
                                JsonNode interactive = msg.path("interactive");
                                String btnTitle = interactive.path("button_reply").path("title").asText(null);
                                String btnId = interactive.path("button_reply").path("id").asText(null);
                                String listTitle = interactive.path("list_reply").path("title").asText(null);
                                String listId = interactive.path("list_reply").path("id").asText(null);
                                bodyText = firstNonBlank(btnTitle, listTitle, btnId, listId);
                                interactiveButtonId = firstNonBlank(btnId, listId);
                            } else if ("image".equals(type)) {
                                JsonNode image = msg.path("image");
                                mediaId = image.path("id").asText(null);
                                mediaMimeType = image.path("mime_type").asText(null);
                                mediaSha256 = image.path("sha256").asText(null);
                                bodyText = image.path("caption").asText(null);
                            } else if ("document".equals(type)) {
                                JsonNode document = msg.path("document");
                                mediaId = document.path("id").asText(null);
                                mediaMimeType = document.path("mime_type").asText(null);
                                mediaFilename = document.path("filename").asText(null);
                                mediaSha256 = document.path("sha256").asText(null);
                                bodyText = document.path("caption").asText(null);
                            } else {
                                bodyText = msg.path("text").path("body").asText(null);
                            }

                            boolean hasMedia = mediaId != null && !mediaId.isBlank();
                            boolean hasText = bodyText != null && !bodyText.isBlank();
                            if (messageId == null || from == null || (!hasText && !hasMedia)) {
                                log.debug("Skipping message due to missing fields: id={} from={} textPresent={} contactName={} type={}", messageId, mask(from), hasText, contactName, type);
                                continue;
                            }

                            MessageReceived messageReceived = new MessageReceived();
                            messageReceived.setWhatsappId(from);
                            messageReceived.setContactName(contactName);
                            messageReceived.setId(messageId);
                            messageReceived.setRepliedTo(replyToMessageId);
                            messageReceived.setMessageType(type);
                            messageReceived.setMessage(bodyText);
                            messageReceived.setInteractiveButtonId(interactiveButtonId);
                            messageReceived.setMediaId(mediaId);
                            messageReceived.setMediaMimeType(mediaMimeType);
                            messageReceived.setMediaFilename(mediaFilename);
                            messageReceived.setMediaSha256(mediaSha256);
                            messageReceivedService.receiveMessage(messageReceived);

                            log.info("Persisted WhatsApp message id={} from={} contactName={} type={} length={} media={}",
                                    messageId, mask(from), contactName, type == null ? "text" : type,
                                    bodyText == null ? 0 : bodyText.length(), hasMedia);
                        }
                    }
                }
            } else {
                log.debug("Unexpected webhook JSON structure, no 'entry' array found");
            }
        } catch (Exception e) {
            log.error("Failed to process WhatsApp webhook: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private String mask(String v) {
        if (v == null || v.isBlank()) return "";
        if (v.length() <= 6) return "***";
        return v.substring(0, 3) + "***" + v.substring(v.length() - 3);
    }

    private String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String s : vals) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }
}
