package com.abba.tanahora.infrastructure.scheduler;

import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.ReminderEvent;
import com.abba.tanahora.domain.model.ReminderEventStatus;
import com.abba.tanahora.domain.service.ReminderEventService;
import com.abba.tanahora.domain.service.ReminderService;
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
public class ReminderMissedJob {

    private static final long MISSED_TIMEOUT_MINUTES = 30;

    private final ReminderService reminderService;
    private final ReminderEventService reminderEventService;

    @Scheduled(fixedDelayString = "${tanahora.scheduler.check-missed-delay-ms:60000}")
    public void markMissedReminders() {
        List<Reminder> reminders = reminderService.getNextRemindersToNotify();
        OffsetDateTime now = OffsetDateTime.now();
        log.info("ReminderMissedJob started: remindersToEvaluate={} now={}", reminders.size(), now);

        reminders.forEach(reminder -> {
            if (!reminder.isActive()) {
                log.debug("Reminder skipped for missed check: reason=INACTIVE reminderId={}", reminder.getId());
                return;
            }

            Optional<ReminderEvent> pendingEvent = reminderEventService.findPendingByReminder(reminder);
            if (pendingEvent.isEmpty()) {
                log.debug("Reminder skipped for missed check: reason=NO_PENDING_EVENT reminderId={}", reminder.getId());
                return;
            }

            ReminderEvent event = pendingEvent.get();
            if (event.getResponseReceivedAt() != null || event.getSentAt() == null) {
                log.debug("Reminder skipped for missed check: reason=INVALID_PENDING_EVENT reminderId={} eventId={} sentAt={} responseReceivedAt={}",
                        reminder.getId(), event.getId(), event.getSentAt(), event.getResponseReceivedAt());
                return;
            }

            if (event.getSentAt().plusMinutes(MISSED_TIMEOUT_MINUTES).isBefore(now)) {
                reminderEventService.updateStatus(event, ReminderEventStatus.MISSED);
                reminderService.updateReminderNextDispatch(reminder);
                log.info("Reminder marked as MISSED: reminderId={} eventId={} sentAt={} now={}",
                        reminder.getId(), event.getId(), event.getSentAt(), now);
                return;
            }

            log.debug("Reminder still within missed timeout: reminderId={} eventId={} sentAt={} deadline={}",
                    reminder.getId(), event.getId(), event.getSentAt(), event.getSentAt().plusMinutes(MISSED_TIMEOUT_MINUTES));
        });

        log.info("ReminderMissedJob finished");
    }
}
