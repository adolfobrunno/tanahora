package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.model.ReminderEvent;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.ReminderEventService;
import com.abba.tanahora.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(355)
@RequiredArgsConstructor
public class CheckHistoryHandler implements MessageHandler {

    private final MessageClassifier messageClassifier;
    private final UserService userService;
    private final ReminderEventService reminderEventService;
    private final NotificationService notificationService;

    @Override
    public boolean supports(AIMessage message) {
        AiMessageProcessorDto classify = messageClassifier.classify(message);
        return classify.getType() == MessageReceivedType.CHECK_HISTORY;
    }

    @Override
    public void handle(AIMessage message) {
        String userId = message.getWhatsappId();
        User user = userService.findByWhatsappId(userId);
        if (user == null) {
            return;
        }

        Map<String, List<ReminderEvent>> groupedByPatient = reminderEventService.findTakenByUserIdGroupedByPatient(userId);
        if (groupedByPatient.isEmpty()) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("""
                            Não encontrei nenhum histórico de tomada ainda.

                            Quando você responder aos lembretes, eu registro aqui.
                            """)
                    .build());
            return;
        }

        Map<String, List<ReminderEvent>> groupedByPatientAndMedication = groupByPatientAndMedication(groupedByPatient);
        groupedByPatientAndMedication.forEach((key, events) -> {
            String[] parts = key.split("\\|", 2);
            String patientName = parts.length > 0 ? parts[0] : "paciente";
            String medicationName = parts.length > 1 ? parts[1] : "medicação";

            StringBuilder builder = new StringBuilder();
            builder.append("Histórico de tomadas do paciente ")
                    .append(patientName)
                    .append(" para ")
                    .append(medicationName)
                    .append(":\n\n");

            for (ReminderEvent event : events) {
                builder.append(formatHistoryLine(event)).append("\n");
            }

            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message(builder.toString().trim())
                    .build());
        });
    }

    private Map<String, List<ReminderEvent>> groupByPatientAndMedication(
            Map<String, List<ReminderEvent>> groupedByPatient) {
        Map<String, List<ReminderEvent>> groupedByPatientAndMedication = new LinkedHashMap<>();
        groupedByPatient.forEach((patientName, events) -> {
            for (ReminderEvent event : events) {
                String medicationName = resolveMedicationName(event);
                String key = patientName + "|" + medicationName;
                groupedByPatientAndMedication
                        .computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(event);
            }
        });
        return groupedByPatientAndMedication;
    }

    private String formatHistoryLine(ReminderEvent event) {
        OffsetDateTime takenAt = event.getResponseReceivedAt() != null
                ? event.getResponseReceivedAt()
                : event.getSentAt();
        String timeLabel = "horário não informado";
        String dateLabel = "";
        if (takenAt != null) {
            timeLabel = takenAt.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
            dateLabel = " (" + takenAt.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM")) + ")";
        }

        return "- " + timeLabel + dateLabel;
    }

    private String resolveMedicationName(ReminderEvent event) {
        if (event.getReminder() != null && event.getReminder().getMedication() != null
                && event.getReminder().getMedication().getName() != null
                && !event.getReminder().getMedication().getName().isBlank()) {
            return event.getReminder().getMedication().getName();
        }
        return "medicação";
    }
}
