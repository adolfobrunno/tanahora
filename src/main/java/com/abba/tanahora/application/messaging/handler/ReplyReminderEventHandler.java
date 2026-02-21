package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.ReminderEvent;
import com.abba.tanahora.domain.model.ReminderEventStatus;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.ReminderEventService;
import com.abba.tanahora.domain.service.ReminderService;
import com.abba.tanahora.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
@Slf4j
@Order(400)
@RequiredArgsConstructor
public class ReplyReminderEventHandler implements MessageHandler {

    private final MessageClassifier messageClassifier;
    private final ReminderEventService reminderEventService;
    private final NotificationService notificationService;
    private final ReminderService reminderService;
    private final UserService userService;

    @Override
    public boolean supports(AIMessage message) {
        AiMessageProcessorDto dto = messageClassifier.classify(message);
        return dto.getType() == MessageReceivedType.REMINDER_RESPONSE_TAKEN ||
                dto.getType() == MessageReceivedType.REMINDER_RESPONSE_SNOOZED;
    }

    @Override
    public void handle(AIMessage message) {
        log.info("Updating reminder event status for message id={} whatsappId={}", message.getId(), message.getWhatsappId());
        AiMessageProcessorDto dto = messageClassifier.classify(message);
        if (dto.getType() == MessageReceivedType.REMINDER_RESPONSE_SNOOZED) {
            handleSnooze(message);
            return;
        }
        Optional<ReminderEvent> reminderEvent = reminderEventService.updateStatusFromResponse(message.getReplyToId(), dto.getType().name(), message.getWhatsappId());
        reminderEvent.ifPresent(event -> {
            Reminder reminder = event.getReminder();
            String messageToResponse = dto.getType() == MessageReceivedType.REMINDER_RESPONSE_TAKEN ?
                    reminder.createTakenConfirmationMessage() : reminder.createSkippedConfirmationMessage();
            notificationService.sendNotification(reminder.getUser(), BasicWhatsAppMessage.builder()
                    .to(reminder.getUser().getWhatsappId())
                    .message(messageToResponse)
                    .build());
            reminderService.updateReminderNextDispatch(reminder);
        });
    }


    private void handleSnooze(AIMessage message) {
        String userId = message.getWhatsappId();
        var user = userService.findByWhatsappId(userId);
        if (user == null) {
            return;
        }
        Optional<ReminderEvent> reminderEvent = reminderEventService.snoozeFromResponse(
                message.getReplyToId(),
                userId,
                Duration.ofHours(1),
                2);

        if (reminderEvent.isEmpty()) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Nao encontrei lembrete para adiar.")
                    .build());
            return;
        }

        ReminderEvent event = reminderEvent.get();
        if (event.getStatus() == ReminderEventStatus.SNOOZED) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Limite de 2 adiamentos atingido. Marquei este lembrete como esquecido.")
                    .build());
            return;
        }

        String time = event.getSnoozedUntil() != null
                ? event.getSnoozedUntil().toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString()
                : "daqui a 1 hora";
        notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                .to(user.getWhatsappId())
                .message(String.format("Ok, adiado. Vou lembrar novamente as %s.", time))
                .build());
    }
}
