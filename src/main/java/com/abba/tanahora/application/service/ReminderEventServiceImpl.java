package com.abba.tanahora.application.service;

import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.ReminderEvent;
import com.abba.tanahora.domain.model.ReminderEventStatus;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.repository.ReminderEventRepository;
import com.abba.tanahora.domain.service.ReminderEventService;
import com.abba.tanahora.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReminderEventServiceImpl implements ReminderEventService {

    private final ReminderEventRepository reminderEventRepository;
    private final UserService userService;

    @Override
    public void registerDispatch(Reminder reminder, String whatsappMessageId) {
        ReminderEvent event = new ReminderEvent();
        event.setReminder(reminder);
        event.setWhatsappMessageId(whatsappMessageId);
        event.setUserWhatsappId(reminder.getUser().getWhatsappId());
        event.setPatientId(reminder.getPatientId());
        event.setPatientName(reminder.getPatientName());
        reminderEventRepository.save(event);
    }

    @Override
    public Optional<ReminderEvent> findPendingByReminder(Reminder reminder) {
        return reminderEventRepository.findFirstByReminderAndStatusOrderBySentAtDesc(reminder, ReminderEventStatus.PENDING);
    }

    @Override
    public void updateStatus(ReminderEvent reminderEvent, ReminderEventStatus reminderEventStatus) {
        reminderEvent.setStatus(reminderEventStatus);
        reminderEventRepository.save(reminderEvent);
    }

    @Override
    public void updateDispatch(ReminderEvent event, String whatsappMessageId) {
        event.setWhatsappMessageId(whatsappMessageId);
        event.setSentAt(OffsetDateTime.now());
        event.setSnoozedUntil(null);
        reminderEventRepository.save(event);
    }

    @Override
    public Optional<ReminderEvent> updateStatusFromResponse(String replyToMessageId, String responseText, String userId) {

        User user = userService.findByWhatsappId(userId);
        Optional<ReminderEvent> event;

        log.debug("Updating reminder event status for replyToMessageId={} responseText={} userId={}", replyToMessageId, responseText, userId);

        if(replyToMessageId == null) {
            event = reminderEventRepository.findLastByUserWhatsappIdAndStatus(user.getWhatsappId(), ReminderEventStatus.PENDING);
        } else {
            event = reminderEventRepository.findFirstByWhatsappMessageId(replyToMessageId);
        }

        ReminderEventStatus reminderEventStatus = resolveStatus(responseText);

        log.debug("Updating reminder event {} status to {}", event, reminderEventStatus);

        event.ifPresent(e -> {
            e.setStatus(reminderEventStatus);
            e.setResponseReceivedAt(OffsetDateTime.now());
            reminderEventRepository.save(e);
        });


        return event;
    }

    @Override
    public Optional<ReminderEvent> snoozeFromResponse(String replyToMessageId, String userId, Duration snoozeDuration, int maxSnoozes) {
        User user = userService.findByWhatsappId(userId);
        Optional<ReminderEvent> event;

        if (replyToMessageId == null) {
            event = reminderEventRepository.findLastByUserWhatsappIdAndStatus(user.getWhatsappId(), ReminderEventStatus.PENDING);
        } else {
            event = reminderEventRepository.findFirstByWhatsappMessageId(replyToMessageId);
        }

        if (event.isEmpty()) {
            return Optional.empty();
        }

        ReminderEvent reminderEvent = event.get();
        if (reminderEvent.getSnoozeCount() >= maxSnoozes) {
            reminderEvent.setStatus(ReminderEventStatus.MISSED);
            reminderEvent.setResponseReceivedAt(OffsetDateTime.now());
            reminderEventRepository.save(reminderEvent);
            return Optional.of(reminderEvent);
        }

        reminderEvent.setSnoozedUntil(OffsetDateTime.now().plus(snoozeDuration));
        reminderEvent.setSnoozeCount(reminderEvent.getSnoozeCount() + 1);
        reminderEventRepository.save(reminderEvent);
        return Optional.of(reminderEvent);
    }

    private ReminderEventStatus resolveStatus(String responseText) {

        MessageReceivedType messageReceivedType = MessageReceivedType.valueOf(responseText);

        return switch (messageReceivedType) {
            case REMINDER_RESPONSE_TAKEN -> ReminderEventStatus.TAKEN;
            case REMINDER_RESPONSE_SNOOZED -> ReminderEventStatus.PENDING;
            default -> ReminderEventStatus.MISSED;
        };
    }
}
