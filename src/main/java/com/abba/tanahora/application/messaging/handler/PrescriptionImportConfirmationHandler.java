package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.flow.FlowState;
import com.abba.tanahora.domain.service.PrescriptionImportService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class PrescriptionImportConfirmationHandler implements HandleAndFlushMessageHandler {

    private static final String CONFIRM_PREFIX = "confirm_prescription:";
    private static final String CANCEL_PREFIX = "cancel_prescription:";

    private final PrescriptionImportService prescriptionImportService;

    public PrescriptionImportConfirmationHandler(PrescriptionImportService prescriptionImportService) {
        this.prescriptionImportService = prescriptionImportService;
    }

    @Override
    public boolean supports(AIMessage message, FlowState state) {
        String action = resolveAction(message);
        return action.startsWith(CONFIRM_PREFIX) || action.startsWith(CANCEL_PREFIX);
    }

    @Override
    public void handleAndFlush(AIMessage message, FlowState state) {
        String action = resolveAction(message);
        if (action.startsWith(CONFIRM_PREFIX)) {
            String importId = action.substring(CONFIRM_PREFIX.length()).trim();
            if (!importId.isBlank()) {
                prescriptionImportService.confirmImport(importId, message.getWhatsappId(), message.getContactName());
            }
            return;
        }

        if (action.startsWith(CANCEL_PREFIX)) {
            String importId = action.substring(CANCEL_PREFIX.length()).trim();
            if (!importId.isBlank()) {
                prescriptionImportService.cancelImport(importId, message.getWhatsappId(), message.getContactName());
            }
        }
    }

    private String resolveAction(AIMessage message) {
        if (message == null) {
            return "";
        }
        if (message.getInteractiveButtonId() != null && !message.getInteractiveButtonId().isBlank()) {
            return message.getInteractiveButtonId().trim();
        }
        return message.getBody() == null ? "" : message.getBody().trim();
    }
}
