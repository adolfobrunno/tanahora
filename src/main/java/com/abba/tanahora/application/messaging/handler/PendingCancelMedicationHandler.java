package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.model.PendingUserAction;
import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.PatientResolverService;
import com.abba.tanahora.domain.service.ReminderService;
import com.abba.tanahora.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(96)
@RequiredArgsConstructor
public class PendingCancelMedicationHandler implements MessageHandler {

    private final UserService userService;
    private final ReminderService reminderService;
    private final NotificationService notificationService;
    private final PatientResolverService patientResolverService;

    @Override
    public boolean supports(AIMessage message) {
        if (message == null || message.getWhatsappId() == null || message.getWhatsappId().isBlank()) {
            return false;
        }
        User user = userService.findByWhatsappId(message.getWhatsappId());
        return user != null && user.getPendingAction() == PendingUserAction.CANCEL_MEDICATION_PATIENT;
    }

    @Override
    public void handle(AIMessage message) {
        User user = userService.findByWhatsappId(message.getWhatsappId());
        if (user == null) {
            return;
        }

        List<Reminder> candidates = resolvePendingCandidates(user);
        if (candidates.isEmpty()) {
            user.clearPendingAction();
            userService.save(user);
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Nao encontrei o contexto do cancelamento anterior. Reenvie o pedido de cancelamento.")
                    .build());
            return;
        }

        String patientIdFromButton = extractPatientIdFromButton(message.getInteractiveButtonId());
        if (patientIdFromButton != null) {
            handleByPatientId(user, candidates, patientIdFromButton);
            return;
        }

        handleByPatientName(user, candidates, message.getBody());
    }

    private List<Reminder> resolvePendingCandidates(User user) {
        List<String> pendingIds = user.getPendingCancelReminderIds();
        if (pendingIds == null || pendingIds.isEmpty()) {
            return List.of();
        }
        Set<String> allowedIds = pendingIds.stream().collect(Collectors.toSet());
        return reminderService.getByUser(user).stream()
                .filter(reminder -> allowedIds.contains(reminder.getId().toString()))
                .toList();
    }

    private void handleByPatientId(User user, List<Reminder> candidates, String patientId) {
        List<Reminder> matches = candidates.stream()
                .filter(reminder -> patientId.equals(reminder.getPatientId()))
                .toList();
        finalizeSelection(user, matches);
    }

    private void handleByPatientName(User user, List<Reminder> candidates, String patientName) {
        if (patientName == null || patientName.isBlank()) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Selecione um paciente nos botoes anteriores ou informe o nome do paciente.")
                    .build());
            return;
        }

        var patient = patientResolverService.resolve(user, patientName, null, false);
        if (patient.isEmpty()) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Nao consegui identificar o paciente. Selecione um dos botoes enviados.")
                    .build());
            return;
        }

        List<Reminder> matches = candidates.stream()
                .filter(reminder -> patient.get().getId().equals(reminder.getPatientId()))
                .toList();
        finalizeSelection(user, matches);
    }

    private void finalizeSelection(User user, List<Reminder> matches) {
        if (matches.isEmpty()) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Nao encontrei lembrete ativo para o paciente selecionado.")
                    .build());
            return;
        }

        if (matches.size() > 1) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Ainda ha mais de um lembrete para esse paciente. Informe tambem o nome do medicamento.")
                    .build());
            return;
        }

        Reminder reminder = matches.getFirst();
        reminderService.cancelReminder(reminder);
        notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                .to(user.getWhatsappId())
                .message(reminder.createCancelNotification())
                .build());
        user.clearPendingAction();
        userService.save(user);
    }

    private String extractPatientIdFromButton(String interactiveButtonId) {
        if (interactiveButtonId == null || interactiveButtonId.isBlank()) {
            return null;
        }
        if (!interactiveButtonId.startsWith(CancelMedicationHandler.PATIENT_BUTTON_PREFIX)) {
            return null;
        }
        String patientId = interactiveButtonId.substring(CancelMedicationHandler.PATIENT_BUTTON_PREFIX.length());
        return patientId.isBlank() ? null : patientId;
    }
}
