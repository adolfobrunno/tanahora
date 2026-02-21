package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.domain.service.PrescriptionImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
@RequiredArgsConstructor
public class PrescriptionImportRequestHandler implements MessageHandler {

    private final PrescriptionImportService prescriptionImportService;

    @Override
    public boolean supports(AIMessage message) {
        if (message == null || message.getMediaId() == null || message.getMediaId().isBlank()) {
            return false;
        }
        String messageType = message.getMessageType();
        return "image".equalsIgnoreCase(messageType) || "document".equalsIgnoreCase(messageType);
    }

    @Override
    public void handle(AIMessage message) {
        prescriptionImportService.startImportFromMediaMessage(message.getId());
    }
}
