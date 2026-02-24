package com.abba.tanahora.application.service;

import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.ReminderEvent;
import com.abba.tanahora.domain.model.ReminderEventStatus;
import com.abba.tanahora.domain.model.ReminderTakenHistory;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.repository.ReminderEventRepository;
import com.abba.tanahora.domain.repository.ReminderTakenHistoryRepository;
import com.abba.tanahora.domain.service.ReminderEventService;
import com.abba.tanahora.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReminderEventServiceImpl implements ReminderEventService {

    private final ReminderEventRepository reminderEventRepository;
    private final ReminderTakenHistoryRepository reminderTakenHistoryRepository;
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
            if (reminderEventStatus == ReminderEventStatus.TAKEN) {
                reminderTakenHistoryRepository.save(buildTakenHistory(e, user));
            }
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

    @Override
    public Map<String, List<ReminderEvent>> findTakenByUserIdGroupedByPatient(String userId) {
        User user = userService.findByWhatsappId(userId);
        if (user == null) {
            return Map.of();
        }

        List<ReminderTakenHistory> histories = reminderTakenHistoryRepository.findAllByUserId(user.getId());
        if (histories.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ReminderTakenHistory> latestHistoryByEventId = latestHistoryByEventId(histories);
        List<ReminderEvent> events = loadEventsByHistory(latestHistoryByEventId);
        if (events.isEmpty()) {
            return Map.of();
        }

        boolean hasPatientHistory = latestHistoryByEventId.values().stream()
                .anyMatch(history -> history.getPatientName() != null && !history.getPatientName().isBlank());
        boolean useUserLabel = (user.getPatients() == null || user.getPatients().isEmpty()) && !hasPatientHistory;
        String fallbackUserName = user.getName() == null || user.getName().isBlank() ? "usuario" : user.getName();

        Map<UUID, String> patientNameByEventId = patientNameByEventId(latestHistoryByEventId);

        return events.stream()
                .sorted(this::compareSentAt)
                .collect(Collectors.groupingBy(
                        event -> resolvePatientLabel(event, useUserLabel, fallbackUserName, patientNameByEventId),
                        LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)));
    }

    private Map<UUID, ReminderTakenHistory> latestHistoryByEventId(List<ReminderTakenHistory> histories) {
        Map<UUID, ReminderTakenHistory> latestHistoryByEventId = new LinkedHashMap<>();
        for (ReminderTakenHistory history : histories) {
            if (history.getEventId() == null) {
                continue;
            }
            UUID eventId = history.getEventId();
            ReminderTakenHistory existing = latestHistoryByEventId.get(eventId);
            if (existing == null) {
                latestHistoryByEventId.put(eventId, history);
                continue;
            }
            OffsetDateTime existingTakenAt = existing.getTakenAt();
            OffsetDateTime candidateTakenAt = history.getTakenAt();
            if (existingTakenAt == null || (candidateTakenAt != null && candidateTakenAt.isAfter(existingTakenAt))) {
                latestHistoryByEventId.put(eventId, history);
            }
        }
        return latestHistoryByEventId;
    }

    private List<ReminderEvent> loadEventsByHistory(Map<UUID, ReminderTakenHistory> latestHistoryByEventId) {
        if (latestHistoryByEventId.isEmpty()) {
            return List.of();
        }
        return reminderEventRepository.findAllById(latestHistoryByEventId.keySet().stream().toList());
    }

    private Map<UUID, String> patientNameByEventId(Map<UUID, ReminderTakenHistory> latestHistoryByEventId) {
        return latestHistoryByEventId.values().stream()
                .filter(history -> history.getPatientName() != null && !history.getPatientName().isBlank())
                .collect(Collectors.toMap(
                        ReminderTakenHistory::getEventId,
                        ReminderTakenHistory::getPatientName,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private int compareSentAt(ReminderEvent left, ReminderEvent right) {
        OffsetDateTime leftSentAt = left.getSentAt();
        OffsetDateTime rightSentAt = right.getSentAt();
        if (leftSentAt == null && rightSentAt == null) {
            return 0;
        }
        if (leftSentAt == null) {
            return 1;
        }
        if (rightSentAt == null) {
            return -1;
        }
        return leftSentAt.compareTo(rightSentAt);
    }

    private String resolvePatientLabel(ReminderEvent event,
                                       boolean useUserLabel,
                                       String fallbackUserName,
                                       Map<UUID, String> patientNameByEventId) {
        if (useUserLabel) {
            return fallbackUserName;
        }
        String patientName = event.getPatientName();
        if (patientName == null || patientName.isBlank()) {
            String historyPatientName = patientNameByEventId.get(event.getId());
            return historyPatientName == null || historyPatientName.isBlank() ? fallbackUserName : historyPatientName;
        }
        return patientName;
    }

    private ReminderTakenHistory buildTakenHistory(ReminderEvent event, User user) {
        ReminderTakenHistory history = new ReminderTakenHistory();
        history.setUserId(user.getId());
        history.setPatientName(event.getPatientName() == null ? user.getName() : event.getPatientName());
        history.setEventId(event.getId());
        history.setTakenAt(event.getResponseReceivedAt() == null ? OffsetDateTime.now() : event.getResponseReceivedAt());
        if (event.getReminder() != null && event.getReminder().getMedication() != null) {
            history.setMedicationName(event.getReminder().getMedication().getName());
        }
        return history;
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
