package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.domain.model.*;
import com.abba.tanahora.domain.repository.ReminderEventRepository;
import com.abba.tanahora.domain.repository.ReminderRepository;
import com.abba.tanahora.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class ReplyReminderEventHandlerDbScripts {

    private final UserRepository userRepository;
    private final ReminderRepository reminderRepository;
    private final ReminderEventRepository reminderEventRepository;

    public ReplyReminderEventHandlerDbScripts(UserRepository userRepository,
                                              ReminderRepository reminderRepository,
                                              ReminderEventRepository reminderEventRepository) {
        this.userRepository = userRepository;
        this.reminderRepository = reminderRepository;
        this.reminderEventRepository = reminderEventRepository;
    }

    public SeedWithPendingEvent insertPendingReminderEventScript(String replyMessageId) {
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setWhatsappId("55" + Math.abs(UUID.randomUUID().getMostSignificantBits()));
        user.setName("Usuario BDD");
        user.setPlan(Plan.PREMIUM);
        user.setProUntil(OffsetDateTime.now().plusMonths(1));
        user = userRepository.save(user);

        Reminder reminder = new Reminder();
        reminder.setUser(user);
        reminder.setPatientId("patient-" + UUID.randomUUID());
        reminder.setPatientName("Paciente Handler");
        reminder.setRrule("FREQ=DAILY;COUNT=10");
        reminder.setNextDispatch(OffsetDateTime.now().minusMinutes(1));
        Medication medication = new Medication();
        medication.setName("Dipirona");
        medication.setDosage("500mg");
        reminder.setMedication(medication);
        reminder = reminderRepository.save(reminder);

        ReminderEvent event = new ReminderEvent();
        event.setReminder(reminder);
        event.setWhatsappMessageId(replyMessageId);
        event.setUserWhatsappId(user.getWhatsappId());
        event.setPatientId(reminder.getPatientId());
        event.setPatientName(reminder.getPatientName());
        event.setStatus(ReminderEventStatus.PENDING);
        event.setSentAt(OffsetDateTime.now().minusMinutes(2));
        event = reminderEventRepository.save(event);

        return new SeedWithPendingEvent(user, reminder, event);
    }

    public record SeedWithPendingEvent(User user, Reminder reminder, ReminderEvent event) {
    }
}
