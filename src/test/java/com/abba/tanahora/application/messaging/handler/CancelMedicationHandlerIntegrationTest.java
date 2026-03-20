package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.messaging.processor.MessageChain;
import com.abba.tanahora.application.notification.WhatsAppMessage;
import com.abba.tanahora.domain.model.PendingUserAction;
import com.abba.tanahora.domain.model.ReminderStatus;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.repository.ReminderRepository;
import com.abba.tanahora.domain.repository.UserRepository;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.utils.Constants;
import com.abba.tanahora.support.MongoCollectionsCleanupExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "tictacmed.scheduler.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(MongoCollectionsCleanupExtension.class)
class CancelMedicationHandlerIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB_CONTAINER = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void registerMongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_DB_CONTAINER::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> "tanahora-tests");
    }

    @Autowired
    private CancelMedicationHandler cancelMedicationHandler;

    @Autowired
    private PendingCancelMedicationHandler pendingCancelMedicationHandler;

    @Autowired
    private CancelMedicationHandlerDbScripts scripts;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageChain messageChain;

    @Autowired
    private NotificationCaptureStore notificationCaptureStore;

    @BeforeEach
    void cleanNotificationStore() {
        notificationCaptureStore.clear();
    }

    @Test
    @DisplayName("Given free user with medication, when cancellation is requested by medication name, then reminder is cancelled")
    void givenFreeUserWhenCancelByMedicationThenCancelsReminder() {
        // Given
        var seed = scripts.createFreeUserWithOneReminderScript("Dipirona", "Ana");
        AIMessage message = cancelIntent(seed.user().getWhatsappId(), "Dipirona", null);

        // When
        boolean supported = cancelMedicationHandler.supports(message);
        cancelMedicationHandler.handle(message);

        // Then
        assertThat(supported).isTrue();
        var updated = reminderRepository.findById(seed.reminders().getFirst().getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReminderStatus.CANCELLED);
        assertThat(notificationCaptureStore.getSentNotifications()).hasSize(1);
    }

    @Test
    @DisplayName("Given premium user with patient informed, when cancellation is requested, then handler cancels matching medication for patient")
    void givenPremiumUserWithPatientWhenCancelThenCancelsMatchingReminder() {
        // Given
        var seed = scripts.createPremiumUserWithOneReminderScript("Dipirona", "Joao");
        AIMessage message = cancelIntent(seed.user().getWhatsappId(), "Dipirona", "Joao");

        // When
        boolean supported = cancelMedicationHandler.supports(message);
        cancelMedicationHandler.handle(message);

        // Then
        assertThat(supported).isTrue();
        var updated = reminderRepository.findById(seed.reminders().getFirst().getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReminderStatus.CANCELLED);
        User user = userRepository.findById(seed.user().getId()).orElseThrow();
        assertThat(user.getPendingAction()).isNull();
    }

    @Test
    @DisplayName("Given premium user without patient name and only one medication match, when cancellation is requested, then handler resolves and cancels")
    void givenPremiumUserWithoutPatientAndSingleMatchWhenCancelThenResolvesAndCancels() {
        // Given
        var seed = scripts.createPremiumUserWithOneReminderScript("Dipirona", "Joao");
        AIMessage message = cancelIntent(seed.user().getWhatsappId(), "Dipirona", null);

        // When
        cancelMedicationHandler.handle(message);

        // Then
        var updated = reminderRepository.findById(seed.reminders().getFirst().getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReminderStatus.CANCELLED);
        User user = userRepository.findById(seed.user().getId()).orElseThrow();
        assertThat(user.getPendingAction()).isNull();
    }

    @Test
    @DisplayName("Given premium user without patient name and ambiguous matches, when cancellation is requested, then handler sets pending action and asks patient name")
    void givenPremiumUserWithoutPatientAndAmbiguousWhenCancelThenSetsPendingAndAsksPatient() {
        // Given
        var seed = scripts.createPremiumUserWithTwoPatientsSameMedicationScript("Dipirona", "Joao", "Maria");
        AIMessage message = cancelIntent(seed.user().getWhatsappId(), "Dipirona", null);

        // When
        cancelMedicationHandler.handle(message);

        // Then
        User user = userRepository.findById(seed.user().getId()).orElseThrow();
        assertThat(user.getPendingAction()).isEqualTo(PendingUserAction.CANCEL_MEDICATION_PATIENT);
        assertThat(user.getPendingActionCreatedAt()).isNotNull();
        assertThat(user.getPendingCancelMedicationName()).isEqualTo("Dipirona");
        assertThat(user.getPendingCancelReminderIds()).hasSize(2);
        assertThat(notificationCaptureStore.getSentNotifications()).hasSize(1);
        String payload = notificationCaptureStore.getSentNotifications().getFirst().payload();
        assertThat(payload)
                .contains("\"type\":\"interactive\"")
                .contains("Selecione o paciente");
        assertThat(payload)
                .contains("cancel_patient:" + seed.reminders().getFirst().getPatientId())
                .contains("cancel_patient:" + seed.reminders().get(1).getPatientId())
                .contains("Joao")
                .contains("Maria");
        assertThat(reminderRepository.findById(seed.reminders().getFirst().getId()).orElseThrow().getStatus())
                .isEqualTo(ReminderStatus.ACTIVE);
        assertThat(reminderRepository.findById(seed.reminders().get(1).getId()).orElseThrow().getStatus())
                .isEqualTo(ReminderStatus.ACTIVE);
    }

    @Test
    @DisplayName("Given pending cancellation and button reply classified as reminder creation, when message chain processes, then pending cancellation flow is executed")
    void givenPendingCancellationWhenMessageChainProcessesThenPendingHandlerWins() {
        // Given
        var seed = scripts.createPremiumUserWithTwoPatientsSameMedicationScript("Dipirona", "Joao", "Maria");
        User user = userRepository.findById(seed.user().getId()).orElseThrow();
        user.setPendingAction(PendingUserAction.CANCEL_MEDICATION_PATIENT);
        user.setPendingCancelContext("Dipirona", seed.reminders().stream().map(r -> r.getId().toString()).toList());
        userRepository.save(user);

        AIMessage message = buttonReply(
                seed.user().getWhatsappId(),
                "cancel_patient:" + seed.reminders().getFirst().getPatientId(),
                "Joao");

        // When
        messageChain.process(message);

        // Then
        User updatedUser = userRepository.findById(seed.user().getId()).orElseThrow();
        assertThat(updatedUser.getPendingAction()).isNull();
        assertThat(reminderRepository.findById(seed.reminders().getFirst().getId()).orElseThrow().getStatus())
                .isEqualTo(ReminderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Given premium user waiting patient selection, when patient name reply resolves uniquely, then handler cancels and clears pending action")
    void givenPremiumPendingPatientWhenReplyResolvesThenCancelsAndClearsPending() {
        // Given
        var seed = scripts.createPremiumUserWithTwoPatientsSameMedicationScript("Dipirona", "Joao", "Maria");
        User user = userRepository.findById(seed.user().getId()).orElseThrow();
        user.setPendingAction(PendingUserAction.CANCEL_MEDICATION_PATIENT);
        user.setPendingCancelContext("Dipirona", seed.reminders().stream().map(r -> r.getId().toString()).toList());
        userRepository.save(user);
        AIMessage message = buttonReply(
                seed.user().getWhatsappId(),
                "cancel_patient:" + seed.reminders().getFirst().getPatientId(),
                "Joao");

        // When
        boolean supported = pendingCancelMedicationHandler.supports(message);
        pendingCancelMedicationHandler.handle(message);

        // Then
        assertThat(supported).isTrue();
        User updatedUser = userRepository.findById(seed.user().getId()).orElseThrow();
        assertThat(updatedUser.getPendingAction()).isNull();
        assertThat(reminderRepository.findById(seed.reminders().getFirst().getId()).orElseThrow().getStatus())
                .isEqualTo(ReminderStatus.CANCELLED);
        assertThat(reminderRepository.findById(seed.reminders().get(1).getId()).orElseThrow().getStatus())
                .isEqualTo(ReminderStatus.ACTIVE);
    }

    @Test
    @DisplayName("Given premium user waiting patient selection, when selected patient button is invalid, then handler keeps pending action and asks again")
    void givenPremiumPendingPatientWhenSelectedButtonInvalidThenKeepsPendingAndAsksAgain() {
        // Given
        var seed = scripts.createPremiumUserWithTwoPatientsSameMedicationScript("Dipirona", "Joao", "Maria");
        User user = userRepository.findById(seed.user().getId()).orElseThrow();
        user.setPendingAction(PendingUserAction.CANCEL_MEDICATION_PATIENT);
        user.setPendingCancelContext("Dipirona", seed.reminders().stream().map(r -> r.getId().toString()).toList());
        userRepository.save(user);
        AIMessage message = buttonReply(seed.user().getWhatsappId(), "cancel_patient:invalid-id", "Invalido");

        // When
        pendingCancelMedicationHandler.handle(message);

        // Then
        User updatedUser = userRepository.findById(seed.user().getId()).orElseThrow();
        assertThat(updatedUser.getPendingAction()).isEqualTo(PendingUserAction.CANCEL_MEDICATION_PATIENT);
        assertThat(notificationCaptureStore.getSentNotifications()).hasSize(1);
        assertThat(notificationCaptureStore.getSentNotifications().getFirst().payload())
                .contains("Nao encontrei lembrete ativo para o paciente selecionado");
        assertThat(reminderRepository.findById(seed.reminders().getFirst().getId()).orElseThrow().getStatus())
                .isEqualTo(ReminderStatus.ACTIVE);
        assertThat(reminderRepository.findById(seed.reminders().get(1).getId()).orElseThrow().getStatus())
                .isEqualTo(ReminderStatus.ACTIVE);
    }

    @Test
    @DisplayName("Given premium user waiting patient selection, when patient reply is unknown, then handler keeps pending action and asks again")
    void givenPremiumPendingPatientWhenReplyUnknownThenKeepsPendingAndAsksAgain() {
        // Given
        var seed = scripts.createPremiumUserWithTwoPatientsSameMedicationScript("Dipirona", "Joao", "Maria");
        User user = userRepository.findById(seed.user().getId()).orElseThrow();
        user.setPendingAction(PendingUserAction.CANCEL_MEDICATION_PATIENT);
        user.setPendingCancelContext("Dipirona", seed.reminders().stream().map(r -> r.getId().toString()).toList());
        userRepository.save(user);
        AIMessage message = plainText(seed.user().getWhatsappId(), "Paciente Inexistente");

        // When
        pendingCancelMedicationHandler.handle(message);

        // Then
        User updatedUser = userRepository.findById(seed.user().getId()).orElseThrow();
        assertThat(updatedUser.getPendingAction()).isEqualTo(PendingUserAction.CANCEL_MEDICATION_PATIENT);
        assertThat(notificationCaptureStore.getSentNotifications()).hasSize(1);
        assertThat(notificationCaptureStore.getSentNotifications().getFirst().payload())
                .contains("Nao consegui identificar o paciente");
        assertThat(reminderRepository.findById(seed.reminders().getFirst().getId()).orElseThrow().getStatus())
                .isEqualTo(ReminderStatus.ACTIVE);
        assertThat(reminderRepository.findById(seed.reminders().get(1).getId()).orElseThrow().getStatus())
                .isEqualTo(ReminderStatus.ACTIVE);
    }

    private AIMessage cancelIntent(String whatsappId, String medication, String patientName) {
        AIMessage message = new AIMessage();
        message.setWhatsappId(whatsappId);
        message.setBody("CANCEL|" + medication + "|" + (patientName == null ? Constants.NOT_INFORMED : patientName));
        return message;
    }

    private AIMessage plainText(String whatsappId, String text) {
        AIMessage message = new AIMessage();
        message.setWhatsappId(whatsappId);
        message.setBody(text);
        return message;
    }

    private AIMessage buttonReply(String whatsappId, String buttonId, String title) {
        AIMessage message = new AIMessage();
        message.setWhatsappId(whatsappId);
        message.setMessageType("interactive");
        message.setInteractiveButtonId(buttonId);
        message.setBody(title);
        return message;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        MessageClassifier messageClassifier() {
            return message -> {
                AiMessageProcessorDto dto = new AiMessageProcessorDto();
                String body = message.getBody();
                if (body != null && body.startsWith("CANCEL|")) {
                    String[] parts = body.split("\\|", -1);
                    dto.setType(MessageReceivedType.REMINDER_CANCEL);
                    dto.setMedication(parts.length > 1 ? parts[1] : "");
                    dto.setPatientName(parts.length > 2 ? parts[2] : Constants.NOT_INFORMED);
                    return dto;
                }
                dto.setType(MessageReceivedType.REMINDER_CREATION);
                dto.setMedication("Medicacao indevida");
                dto.setPatientName(Constants.NOT_INFORMED);
                return dto;
            };
        }

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
