package com.abba.tanahora.infrastructure.scheduler;

import com.abba.tanahora.application.notification.InteractiveWhatsAppMessage;
import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.ReminderEvent;
import com.abba.tanahora.domain.model.ReminderEventStatus;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.ReminderEventService;
import com.abba.tanahora.domain.service.ReminderService;
import com.whatsapp.api.domain.messages.Button;
import com.whatsapp.api.domain.messages.Reply;
import com.whatsapp.api.domain.messages.type.ButtonType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "tictacmed.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ReminderSnoozedJob {

    private final ReminderService reminderService;
    private final ReminderEventService reminderEventService;
    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${tanahora.scheduler.send-snoozed-delay-ms:60000}")
    public void sendSnoozedNotifications() {
        List<Reminder> reminders = reminderService.getNextRemindersToNotify();
        OffsetDateTime now = OffsetDateTime.now();
        log.info("ReminderSnoozedJob started: remindersToEvaluate={} now={}", reminders.size(), now);

        reminders.forEach(reminder -> {
            if (!reminder.isActive()) {
                log.debug("Reminder skipped for snoozed check: reason=INACTIVE reminderId={}", reminder.getId());
                return;
            }

            Optional<ReminderEvent> snoozedEvent = reminderEventService.findLatestByReminderAndStatus(reminder, ReminderEventStatus.SNOOZED);
            if (snoozedEvent.isEmpty()) {
                log.debug("Reminder skipped for snoozed check: reason=NO_SNOOZED_EVENT reminderId={}", reminder.getId());
                return;
            }

            ReminderEvent event = snoozedEvent.get();
            if (event.getSnoozedUntil() == null || event.getSnoozedUntil().isAfter(now)) {
                log.debug("Reminder skipped for snoozed resend: reason=SNOOZE_NOT_DUE reminderId={} eventId={} snoozedUntil={}",
                        reminder.getId(), event.getId(), event.getSnoozedUntil());
                return;
            }

            String messageId = notificationService.sendNotification(reminder.getUser(), InteractiveWhatsAppMessage
                    .builder()
                    .to(reminder.getUser().getWhatsappId())
                    .text(reminder.createSendReminderMessage())
                    .button(new Button().setType(ButtonType.REPLY).setReply(new Reply().setTitle("Tomei").setId("tomei_btn")))
                    .button(new Button().setType(ButtonType.REPLY).setReply(new Reply().setTitle("Adiar").setId("adiar_btn")))
                    .button(new Button().setType(ButtonType.REPLY).setReply(new Reply().setTitle("Pular").setId("pular_btn")))
                    .build());

            reminderEventService.updateDispatch(event, messageId);
            log.info("Snoozed reminder resent: reminderId={} eventId={} userId={} messageId={}",
                    reminder.getId(), event.getId(), reminder.getUser().getId(), messageId);
        });

        log.info("ReminderSnoozedJob finished");
    }
}
