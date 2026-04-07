package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.domain.model.*;
import com.abba.tanahora.domain.repository.ReminderRepository;
import com.abba.tanahora.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CancelMedicationHandlerDbScripts {

    private final UserRepository userRepository;
    private final ReminderRepository reminderRepository;

    public CancelMedicationHandlerDbScripts(UserRepository userRepository, ReminderRepository reminderRepository) {
        this.userRepository = userRepository;
        this.reminderRepository = reminderRepository;
    }

    public Seed createFreeUserWithOneReminderScript(String medicationName, String patientName) {
        User user = createUser(false);
        Patient patient = addPatient(user, patientName);
        Reminder reminder = createReminder(user, patient, medicationName);
        return new Seed(user, List.of(reminder));
    }

    public Seed createPremiumUserWithOneReminderScript(String medicationName, String patientName) {
        User user = createUser(true);
        Patient patient = addPatient(user, patientName);
        Reminder reminder = createReminder(user, patient, medicationName);
        return new Seed(user, List.of(reminder));
    }

    public Seed createPremiumUserWithTwoPatientsSameMedicationScript(String medicationName, String firstPatient, String secondPatient) {
        User user = createUser(true);
        Patient patientOne = addPatient(user, firstPatient);
        Patient patientTwo = addPatient(user, secondPatient);

        Reminder reminderOne = createReminder(user, patientOne, medicationName);
        Reminder reminderTwo = createReminder(user, patientTwo, medicationName);
        return new Seed(user, List.of(reminderOne, reminderTwo));
    }

    private User createUser(boolean premium) {
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setWhatsappId("55" + Math.abs(UUID.randomUUID().getMostSignificantBits()));
        user.setName("Usuario Cancelamento");
        user.setPatients(new ArrayList<>());
        if (premium) {
            user.setPlan(Plan.PREMIUM);
            user.setProUntil(OffsetDateTime.now().plusMonths(1));
        } else {
            user.setPlan(Plan.FREE);
            user.setProUntil(null);
        }
        return userRepository.save(user);
    }

    private Patient addPatient(User user, String patientName) {
        Patient patient = new Patient();
        patient.setName(patientName);
        user.getPatients().add(patient);
        userRepository.save(user);
        return patient;
    }

    private Reminder createReminder(User user, Patient patient, String medicationName) {
        Reminder reminder = new Reminder();
        reminder.setUser(user);
        reminder.setPatientId(patient.getId());
        reminder.setPatientName(patient.getName());
        reminder.setRrule("FREQ=DAILY;COUNT=10");
        reminder.setNextDispatch(OffsetDateTime.now().plusHours(1));

        Medication medication = new Medication();
        medication.setName(medicationName);
        medication.setDosage("500mg");
        reminder.setMedication(medication);

        return reminderRepository.save(reminder);
    }

    public record Seed(User user, List<Reminder> reminders) {
    }
}
