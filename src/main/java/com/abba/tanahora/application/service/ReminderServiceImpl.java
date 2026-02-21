package com.abba.tanahora.application.service;

import com.abba.tanahora.application.exceptions.ReminderLimitException;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.exceptions.InvalidRruleException;
import com.abba.tanahora.domain.model.*;
import com.abba.tanahora.domain.repository.ReminderRepository;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.abba.tanahora.domain.utils.Constants.BRAZIL_ZONEID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {

    private final ReminderRepository reminderRepository;
    private final NotificationService notificationService;

    @Override
    public Reminder scheduleMedication(User user, PatientRef patient, Medication med, String rrule) {

        if (rrule == null || rrule.isBlank()) {
            throw new InvalidRruleException("rrule cannot be blank");
        }

        if (canCreateReminder(user)) {
            Reminder reminder = new Reminder();
            reminder.setMedication(med);
            reminder.setRrule(rrule);
            reminder.setStatus(ReminderStatus.ACTIVE);
            reminder.setUser(user);
            reminder.setPatientId(patient.getId());
            reminder.setPatientName(patient.getName());
            reminder.updateNextDispatch();
            return reminderRepository.save(reminder);
        } else {
            throw new ReminderLimitException("No premium user already has a reminder");
        }
    }

    @Override
    public Optional<OffsetDateTime> getNextDispatch(Reminder reminder) {
        return Optional.ofNullable(reminder.getNextDispatch());
    }

    @Override
    public List<Reminder> getNextRemindersToNotify() {
        return reminderRepository.findPendingNextDispatch(OffsetDateTime.now(BRAZIL_ZONEID));
    }

    @Override
    public List<Reminder> getByUser(User user) {
        return reminderRepository.findByUserAndStatus(user, ReminderStatus.ACTIVE);
    }

    @Override
    public void cancelReminder(Reminder reminder) {
        reminder.cancelReminder();
        reminderRepository.save(reminder);
    }

    @Override
    public void updateReminderNextDispatch(Reminder reminder) {
        reminder.updateNextDispatch();
        reminderRepository.save(reminder);
        if (reminder.getStatus() == ReminderStatus.ACTIVE) {
            notificationService.sendNotification(reminder.getUser(), BasicWhatsAppMessage.builder()
                    .to(reminder.getUser().getWhatsappId())
                    .message(reminder.createNextDispatchMessage())
                    .build());
        } else if (reminder.getStatus() == ReminderStatus.COMPLETED) {
            notificationService.sendNotification(reminder.getUser(), BasicWhatsAppMessage.builder()
                    .to(reminder.getUser().getWhatsappId())
                    .message(reminder.createCompletedMessage())
                    .build());
        }
    }

    private boolean canCreateReminder(User user) {
        if (user.isPremium()) {
            return true;
        }

        List<Reminder> currents = reminderRepository.findByUserAndStatus(user, ReminderStatus.ACTIVE);
        return currents.isEmpty();
    }
}
