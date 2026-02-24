package com.abba.tanahora.domain.service;

import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.ReminderEvent;
import com.abba.tanahora.domain.model.ReminderEventStatus;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ReminderEventService {

    void registerDispatch(Reminder reminder, String whatsappMessageId);

    Optional<ReminderEvent> findPendingByReminder(Reminder reminder);

    void updateStatus(ReminderEvent reminderEvent, ReminderEventStatus reminderEventStatus);

    void updateDispatch(ReminderEvent event, String whatsappMessageId);

    Optional<ReminderEvent> updateStatusFromResponse(String replyToMessageId, String responseText, String userId);

    Optional<ReminderEvent> snoozeFromResponse(String replyToMessageId, String userId, Duration snoozeDuration, int maxSnoozes);

    Map<String, List<ReminderEvent>> findTakenByUserIdGroupedByPatient(String userId);
}
