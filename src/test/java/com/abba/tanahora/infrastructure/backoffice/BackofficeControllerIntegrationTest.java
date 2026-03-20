package com.abba.tanahora.infrastructure.backoffice;

import com.abba.tanahora.application.notification.WhatsAppMessage;
import com.abba.tanahora.domain.model.*;
import com.abba.tanahora.domain.repository.ReminderRepository;
import com.abba.tanahora.domain.repository.UserRepository;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.support.MongoCollectionsCleanupExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "tictacmed.scheduler.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(MongoCollectionsCleanupExtension.class)
class BackofficeControllerIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB_CONTAINER = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void registerMongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_DB_CONTAINER::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> "tanahora-tests");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private NotificationCaptureStore notificationCaptureStore;

    @BeforeEach
    void cleanNotificationStore() {
        notificationCaptureStore.clear();
    }

    @Test
    @DisplayName("Given existing user, when backoffice sends message, then notification is dispatched")
    void givenExistingUserWhenSendMessageThenDispatchesNotification() throws Exception {
        // Given
        User user = createUser("5511999990001", Plan.FREE);

        // When / Then
        mockMvc.perform(post("/backoffice/users/{whatsappId}/messages", user.getWhatsappId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Mensagem do backoffice"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value("msg-1"));

        assertThat(notificationCaptureStore.getSentNotifications()).hasSize(1);
        assertThat(notificationCaptureStore.getSentNotifications().getFirst().payload())
                .contains("Mensagem do backoffice");
    }

    @Test
    @DisplayName("Given existing free user, when backoffice marks as PREMIUM and then FREE, then plan changes accordingly")
    void givenFreeUserWhenUpdatePlanThenUpdatesPremiumAndFree() throws Exception {
        // Given
        User user = createUser("5511999990002", Plan.FREE);

        // When / Then premium
        mockMvc.perform(post("/backoffice/users/{whatsappId}/plan", user.getWhatsappId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":"PREMIUM","months":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"));

        User premium = userRepository.findByWhatsappId(user.getWhatsappId()).orElseThrow();
        assertThat(premium.getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(premium.getProUntil()).isNotNull();

        // When / Then free
        mockMvc.perform(post("/backoffice/users/{whatsappId}/plan", user.getWhatsappId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":"FREE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"));

        User freeAgain = userRepository.findByWhatsappId(user.getWhatsappId()).orElseThrow();
        assertThat(freeAgain.getPlan()).isEqualTo(Plan.FREE);
        assertThat(freeAgain.getProUntil()).isNull();
    }

    @Test
    @DisplayName("Given user with multiple active reminders, when backoffice requests next reminder, then earliest nextDispatch is returned")
    void givenUserWithRemindersWhenGetNextReminderThenReturnsEarliest() throws Exception {
        // Given
        User user = createUser("5511999990003", Plan.PREMIUM);
        Reminder later = createReminder(user, "Paciente B", "Dipirona", OffsetDateTime.now().plusHours(3), ReminderStatus.ACTIVE);
        Reminder earlier = createReminder(user, "Paciente A", "Paracetamol", OffsetDateTime.now().plusMinutes(30), ReminderStatus.ACTIVE);

        // When / Then
        mockMvc.perform(get("/backoffice/users/{whatsappId}/next-reminder", user.getWhatsappId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasReminder").value(true))
                .andExpect(jsonPath("$.reminderId").value(earlier.getId().toString()))
                .andExpect(jsonPath("$.patientName").value("Paciente A"))
                .andExpect(jsonPath("$.medicationName").value("Paracetamol"));

        assertThat(later.getId()).isNotEqualTo(earlier.getId());
    }

    @Test
    @DisplayName("Given active and inactive reminders, when backoffice requests active stats, then returns active users and reminders counts")
    void givenMixedRemindersWhenGetActiveStatsThenReturnsCounts() throws Exception {
        // Given
        User userOne = createUser("5511999990004", Plan.FREE);
        User userTwo = createUser("5511999990005", Plan.PREMIUM);
        User userThree = createUser("5511999990006", Plan.FREE);

        createReminder(userOne, "Paciente 1", "Dipirona", OffsetDateTime.now().plusHours(1), ReminderStatus.ACTIVE);
        createReminder(userTwo, "Paciente 2", "Paracetamol", OffsetDateTime.now().plusHours(2), ReminderStatus.ACTIVE);
        createReminder(userTwo, "Paciente 2", "Ibuprofeno", OffsetDateTime.now().plusHours(4), ReminderStatus.ACTIVE);
        createReminder(userThree, "Paciente 3", "Amoxicilina", OffsetDateTime.now().plusHours(3), ReminderStatus.CANCELLED);

        // When / Then
        mockMvc.perform(get("/backoffice/stats/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(3))
                .andExpect(jsonPath("$.activeUsers").value(2))
                .andExpect(jsonPath("$.activeReminders").value(3));
    }

    private User createUser(String whatsappId, Plan plan) {
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setWhatsappId(whatsappId);
        user.setName("Backoffice User");
        user.setPatients(new ArrayList<>());
        user.setPlan(plan);
        if (plan == Plan.PREMIUM) {
            user.setProUntil(OffsetDateTime.now().plusMonths(1));
        }
        return userRepository.save(user);
    }

    private Reminder createReminder(User user, String patientName, String medicationName, OffsetDateTime nextDispatch, ReminderStatus status) {
        PatientRef patient = new PatientRef();
        patient.setName(patientName);
        user.getPatients().add(patient);
        userRepository.save(user);

        Reminder reminder = new Reminder();
        reminder.setUser(user);
        reminder.setPatientId(patient.getId());
        reminder.setPatientName(patient.getName());
        reminder.setRrule("FREQ=DAILY;COUNT=10");
        reminder.setNextDispatch(nextDispatch);
        reminder.setStatus(status);

        Medication medication = new Medication();
        medication.setName(medicationName);
        medication.setDosage("500mg");
        reminder.setMedication(medication);
        return reminderRepository.save(reminder);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        NotificationCaptureStore notificationCaptureStore() {
            return new NotificationCaptureStore();
        }

        @Bean
        @Primary
        NotificationService notificationService(NotificationCaptureStore notificationCaptureStore) {
            return (user, message) -> notificationCaptureStore.capture(user, message);
        }
    }

    static class NotificationCaptureStore {
        private final AtomicInteger sequence = new AtomicInteger();
        private final List<SentNotification> sentNotifications = new ArrayList<>();

        String capture(User user, WhatsAppMessage message) {
            String messageId = "msg-" + sequence.incrementAndGet();
            sentNotifications.add(new SentNotification(user.getId(), messageId, message.buildPayload()));
            return messageId;
        }

        List<SentNotification> getSentNotifications() {
            return sentNotifications;
        }

        void clear() {
            sentNotifications.clear();
            sequence.set(0);
        }
    }

    record SentNotification(String userId, String messageId, String payload) {
    }
}
