package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.flow.FlowState;
import com.abba.tanahora.domain.service.PrescriptionImportService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class PrescriptionImportRequestHandler implements HandleAndFlushMessageHandler {

    private final PrescriptionImportService prescriptionImportService;

    public PrescriptionImportRequestHandler(PrescriptionImportService prescriptionImportService) {
        this.prescriptionImportService = prescriptionImportService;
    }

    @Override
    public boolean supports(AIMessage message, FlowState state) {
        if (message == null || message.getMediaId() == null || message.getMediaId().isBlank()) {
            return false;
        }
        String messageType = message.getMessageType();
        return "image".equalsIgnoreCase(messageType) || "document".equalsIgnoreCase(messageType);
    }

    @Override
    public void handleAndFlush(AIMessage message, FlowState state) {
        prescriptionImportService.startImportFromMediaMessage(message.getId());
    }
}
