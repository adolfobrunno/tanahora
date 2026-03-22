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

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "tictacmed.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ReminderSenderJob {

    private final ReminderService reminderService;
    private final ReminderEventService reminderEventService;
    private final NotificationService notificationService;


    @Scheduled(fixedDelayString = "${tanahora.scheduler.send-reminders-delay-ms}")
    public void sendRemindNotification() {

        List<Reminder> reminders = reminderService.getNextRemindersToNotify();
        log.info("ReminderSenderJob started: remindersToEvaluate={}", reminders.size());

        reminders.forEach(reminder -> {
            if (!reminder.isActive()) {
                log.debug("Reminder skipped: reason=INACTIVE reminderId={}", reminder.getId());
                return;
            }

            Optional<ReminderEvent> pendingEvent = reminderEventService.findPendingByReminder(reminder);
            if (pendingEvent.isPresent()) {
                log.debug("Reminder skipped: reason=PENDING_EVENT_EXISTS reminderId={} eventId={}",
                        reminder.getId(), pendingEvent.get().getId());
                return;
            }

            Optional<ReminderEvent> snoozedEvent = reminderEventService.findLatestByReminderAndStatus(reminder, ReminderEventStatus.SNOOZED);
            if (snoozedEvent.isPresent()) {
                log.debug("Reminder skipped: reason=SNOOZED_EVENT_EXISTS reminderId={} eventId={} snoozedUntil={}",
                        reminder.getId(), snoozedEvent.get().getId(), snoozedEvent.get().getSnoozedUntil());
                return;
            }

            String messageId = notificationService.sendNotification(reminder.getUser(), InteractiveWhatsAppMessage
                    .builder()
                    .to(reminder.getUser().getWhatsappId())
                    .text(reminder.createSendReminderMessage())
                    .button(new Button().setType(ButtonType.REPLY).setReply(new Reply().setTitle("Tomei").setId("tomei_btn")))
                    .button(new Button().setType(ButtonType.REPLY).setReply(new Reply().setTitle("Adiar por uma hora").setId("adiar_btn")))
                    .button(new Button().setType(ButtonType.REPLY).setReply(new Reply().setTitle("Pular").setId("pular_btn")))
                    .build());
            reminderEventService.registerDispatch(reminder, messageId);
            log.info("Reminder sent: reminderId={} userId={} messageId={}",
                    reminder.getId(), reminder.getUser().getId(), messageId);
        });

        log.info("ReminderSenderJob finished");
    }

}
