package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.application.notification.InteractiveWhatsAppMessage;
import com.abba.tanahora.domain.model.PendingUserAction;
import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.PatientResolverService;
import com.abba.tanahora.domain.service.ReminderService;
import com.abba.tanahora.domain.service.UserService;
import com.abba.tanahora.domain.utils.Constants;
import com.whatsapp.api.domain.messages.Button;
import com.whatsapp.api.domain.messages.Reply;
import com.whatsapp.api.domain.messages.type.ButtonType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
@Order(500)
@RequiredArgsConstructor
public class CancelMedicationHandler implements MessageHandler {

    static final String PATIENT_BUTTON_PREFIX = "cancel_patient:";

    private final MessageClassifier messageClassifier;
    private final UserService userService;
    private final ReminderService reminderService;
    private final NotificationService notificationService;
    private final PatientResolverService patientResolverService;

    @Override
    public boolean supports(AIMessage message) {
        AiMessageProcessorDto dto = messageClassifier.classify(message);
        return dto != null && dto.getType() == MessageReceivedType.REMINDER_CANCEL;
    }

    @Override
    public void handle(AIMessage message) {
        User user = userService.findByWhatsappId(message.getWhatsappId());
        if (user == null) {
            return;
        }

        AiMessageProcessorDto dto = messageClassifier.classify(message);
        if (dto == null || dto.getType() != MessageReceivedType.REMINDER_CANCEL) {
            return;
        }

        String medicationName = dto.getMedication();
        List<Reminder> remindersByMedication = reminderService.getByUser(user)
                .stream()
                .filter(reminder -> reminder.getMedication() != null)
                .filter(reminder -> reminder.getMedication().getName() != null)
                .filter(reminder -> reminder.getMedication().getName().equalsIgnoreCase(medicationName))
                .collect(Collectors.toList());

        if (!user.isPremium()) {
            handleFreeUserCancellation(user, medicationName, remindersByMedication);
            return;
        }

        if (isPatientNotProvided(dto.getPatientName())) {
            handlePremiumWithoutPatient(user, medicationName, remindersByMedication);
            return;
        }

        var patient = patientResolverService.resolve(user, dto.getPatientName(), null, false);
        if (patient.isEmpty()) {
            sendMedicationNotFound(user, medicationName);
            return;
        }

        Optional<Reminder> reminderMatch = remindersByMedication
                .stream()
                .filter(reminder -> patient.get().getId().equals(reminder.getPatientId()))
                .findFirst();

        if (reminderMatch.isPresent()) {
            cancelAndNotify(user, reminderMatch.get());
            user.clearPendingAction();
            userService.save(user);
            return;
        }

        sendMedicationNotFound(user, medicationName);
    }

    private void handleFreeUserCancellation(User user, String medicationName, List<Reminder> remindersByMedication) {
        if (remindersByMedication.isEmpty()) {
            sendMedicationNotFound(user, medicationName);
            return;
        }
        cancelAndNotify(user, remindersByMedication.getFirst());
        user.clearPendingAction();
        userService.save(user);
    }

    private void handlePremiumWithoutPatient(User user, String medicationName, List<Reminder> remindersByMedication) {
        if (remindersByMedication.isEmpty()) {
            sendMedicationNotFound(user, medicationName);
            return;
        }

        List<Reminder> distinctPatientMatches = remindersByMedication.stream()
                .collect(Collectors.toMap(Reminder::getPatientId, reminder -> reminder, (first, ignored) -> first))
                .values()
                .stream()
                .toList();

        if (distinctPatientMatches.size() == 1) {
            cancelAndNotify(user, distinctPatientMatches.getFirst());
            user.clearPendingAction();
            userService.save(user);
            return;
        }

        user.setPendingAction(PendingUserAction.CANCEL_MEDICATION_PATIENT);
        user.setPendingCancelContext(medicationName,
                distinctPatientMatches.stream().map(reminder -> reminder.getId().toString()).toList());
        userService.save(user);
        sendAskPatientNameWithButtons(user, medicationName, distinctPatientMatches);
    }

    private void sendAskPatientNameWithButtons(User user, String medicationName, List<Reminder> remindersByMedication) {
        var builder = InteractiveWhatsAppMessage.builder()
                .to(user.getWhatsappId())
                .text(String.format("Encontrei mais de um paciente com o medicamento \"%s\". Selecione o paciente:", medicationName));

        remindersByMedication.forEach(reminder -> builder.button(new Button()
                .setType(ButtonType.REPLY)
                .setReply(new Reply()
                        .setId(PATIENT_BUTTON_PREFIX + reminder.getPatientId())
                        .setTitle(limitTitle(patientLabel(reminder))))));

        notificationService.sendNotification(user, builder.build());
    }

    private String limitTitle(String label) {
        if (label == null || label.isBlank()) {
            return "Paciente";
        }
        String trimmed = label.trim();
        if (trimmed.length() <= 20) {
            return trimmed;
        }
        return trimmed.substring(0, 20);
    }

    private void sendMedicationNotFound(User user, String medicationName) {
        log.warn("Medication {} not found for user {}", medicationName, user.getWhatsappId());
        notificationService.sendNotification(user, BasicWhatsAppMessage.builder().to(user.getWhatsappId()).message(String.format(
                """
                        Ops! Parece que a medicacao que voce informou nao esta registrada.
                        
                        Confira se o nome \"%s\" esta correto ou se voce ja havia cancelado essa medicacao anteriormente.
                        """, medicationName)
        ).build());
    }

    private void cancelAndNotify(User user, Reminder reminder) {
        reminderService.cancelReminder(reminder);
        notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                .to(user.getWhatsappId())
                .message(reminder.createCancelNotification())
                .build());
    }

    private String patientLabel(Reminder reminder) {
        if (reminder.getPatientName() != null && !reminder.getPatientName().isBlank()) {
            return reminder.getPatientName();
        }
        return "paciente";
    }

    private boolean isPatientNotProvided(String patientName) {
        if (patientName == null || patientName.isBlank()) {
            return true;
        }

        String normalized = normalize(patientName);
        return normalized.equals(normalize(Constants.NOT_INFORMED)) || normalized.equals("nao informado");
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT).trim();
    }
}
