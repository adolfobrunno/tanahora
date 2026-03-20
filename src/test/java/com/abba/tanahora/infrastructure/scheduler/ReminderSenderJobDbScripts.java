package com.abba.tanahora.infrastructure.scheduler;

import com.abba.tanahora.domain.model.*;
import com.abba.tanahora.domain.repository.ReminderEventRepository;
import com.abba.tanahora.domain.repository.ReminderRepository;
import com.abba.tanahora.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class ReminderSenderJobDbScripts {

    private final UserRepository userRepository;
    private final ReminderRepository reminderRepository;
    private final ReminderEventRepository reminderEventRepository;

    public ReminderSenderJobDbScripts(UserRepository userRepository,
                                      ReminderRepository reminderRepository,
                                      ReminderEventRepository reminderEventRepository) {
        this.userRepository = userRepository;
        this.reminderRepository = reminderRepository;
        this.reminderEventRepository = reminderEventRepository;
    }

    public Reminder insertReminderReadyToSendScript() {
        User user = insertUserScript("reminder-ready");
        return insertReminderScript(user, OffsetDateTime.now().minusMinutes(1));
    }

    public SeedWithPendingEvent insertReminderWithPendingOverdueEventScript() {
        User user = insertUserScript("pending-overdue");
        Reminder reminder = insertReminderScript(user, OffsetDateTime.now().minusMinutes(40));

        ReminderEvent event = new ReminderEvent();
        event.setReminder(reminder);
        event.setUserWhatsappId(user.getWhatsappId());
        event.setWhatsappMessageId("msg-old-pending");
        event.setPatientId(reminder.getPatientId());
        event.setPatientName(reminder.getPatientName());
        event.setSentAt(OffsetDateTime.now().minusMinutes(31));
        event.setStatus(ReminderEventStatus.PENDING);
        event = reminderEventRepository.save(event);

        return new SeedWithPendingEvent(reminder, event);
    }

    public SeedWithSnoozedEvent insertReminderWithSnoozedDueEventScript() {
        User user = insertUserScript("snoozed-due");
        Reminder reminder = insertReminderScript(user, OffsetDateTime.now().minusMinutes(40));

        ReminderEvent event = new ReminderEvent();
        event.setReminder(reminder);
        event.setUserWhatsappId(user.getWhatsappId());
        event.setWhatsappMessageId("msg-old-snoozed");
        event.setPatientId(reminder.getPatientId());
        event.setPatientName(reminder.getPatientName());
        event.setSentAt(OffsetDateTime.now().minusMinutes(35));
        event.setResponseReceivedAt(OffsetDateTime.now().minusMinutes(34));
        event.setStatus(ReminderEventStatus.SNOOZED);
        event.setSnoozedUntil(OffsetDateTime.now().minusMinutes(1));
        event = reminderEventRepository.save(event);

        return new SeedWithSnoozedEvent(reminder, event);
    }

    public Reminder insertReminderWithPreviousMissedEventScript() {
        User user = insertUserScript("with-missed");
        Reminder reminder = insertReminderScript(user, OffsetDateTime.now().minusMinutes(2));

        ReminderEvent missedEvent = new ReminderEvent();
        missedEvent.setReminder(reminder);
        missedEvent.setUserWhatsappId(user.getWhatsappId());
        missedEvent.setWhatsappMessageId("msg-old-missed");
        missedEvent.setPatientId(reminder.getPatientId());
        missedEvent.setPatientName(reminder.getPatientName());
        missedEvent.setSentAt(OffsetDateTime.now().minusHours(1));
        missedEvent.setResponseReceivedAt(OffsetDateTime.now().minusMinutes(45));
        missedEvent.setStatus(ReminderEventStatus.MISSED);
        reminderEventRepository.save(missedEvent);

        return reminder;
    }

    private User insertUserScript(String suffix) {
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setWhatsappId("55" + Math.abs(UUID.randomUUID().getMostSignificantBits()));
        user.setName("Usuário " + suffix);
        user.setEmail(suffix + "@example.com");
        user.setPlan(Plan.PREMIUM);
        user.setProUntil(OffsetDateTime.now().plusMonths(1));
        return userRepository.save(user);
    }

    private Reminder insertReminderScript(User user, OffsetDateTime nextDispatch) {
        Reminder reminder = new Reminder();
        reminder.setUser(user);
        reminder.setPatientId("patient-" + UUID.randomUUID());
        reminder.setPatientName("Paciente Teste");
        reminder.setNextDispatch(nextDispatch);
        reminder.setRrule("FREQ=DAILY;COUNT=10");

        Medication medication = new Medication();
        medication.setName("Dipirona");
        medication.setDosage("500mg");
        reminder.setMedication(medication);

        return reminderRepository.save(reminder);
    }

    public record SeedWithPendingEvent(Reminder reminder, ReminderEvent pendingEvent) {
    }

    public record SeedWithSnoozedEvent(Reminder reminder, ReminderEvent snoozedEvent) {
    }
}
