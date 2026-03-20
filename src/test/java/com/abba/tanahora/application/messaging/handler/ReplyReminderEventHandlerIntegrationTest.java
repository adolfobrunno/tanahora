package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.notification.WhatsAppMessage;
import com.abba.tanahora.domain.model.ReminderEvent;
import com.abba.tanahora.domain.model.ReminderEventStatus;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.repository.ReminderEventRepository;
import com.abba.tanahora.domain.service.NotificationService;
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
class ReplyReminderEventHandlerIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB_CONTAINER = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void registerMongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_DB_CONTAINER::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> "tanahora-tests");
    }

    @Autowired
    private ReplyReminderEventHandler replyReminderEventHandler;

    @Autowired
    private ReplyReminderEventHandlerDbScripts scripts;

    @Autowired
    private ReminderEventRepository reminderEventRepository;

    @Autowired
    private NotificationCaptureStore notificationCaptureStore;

    @BeforeEach
    void cleanNotificationStore() {
        notificationCaptureStore.clear();
    }

    @Test
    @DisplayName("Given a pending reminder event and Tomei button response, " +
            "when handler processes message, " +
            "then event is marked as TAKEN and confirmation is sent")
    void givenPendingEventAndTakenButtonWhenHandleThenMarksTakenAndSendsConfirmation() {
        // Given
        var seed = scripts.insertPendingReminderEventScript("reply-msg-1");
        AIMessage message = createReplyMessage(seed.user().getWhatsappId(), "reply-msg-1", "tomei_btn", "Tomei");

        // When
        boolean supported = replyReminderEventHandler.supports(message);
        replyReminderEventHandler.handle(message);

        // Then
        assertThat(supported).isTrue();
        ReminderEvent updated = reminderEventRepository.findById(seed.event().getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReminderEventStatus.TAKEN);
        assertThat(updated.getResponseReceivedAt()).isNotNull();
        assertThat(notificationCaptureStore.getSentNotifications()).hasSize(2);
        assertThat(notificationCaptureStore.getSentNotifications().getFirst().payload())
                .contains("Registramos que o paciente");
    }

    @Test
    @DisplayName("Given a pending reminder event and Adiar button response," +
            "when handler processes message, " +
            "then event is snoozed and user is notified")
    void givenPendingEventAndSnoozeButtonWhenHandleThenSnoozesAndNotifies() {
        // Given
        var seed = scripts.insertPendingReminderEventScript("reply-msg-2");
        AIMessage message = createReplyMessage(seed.user().getWhatsappId(), "reply-msg-2", "adiar_btn", "Adiar");

        // When
        boolean supported = replyReminderEventHandler.supports(message);
        replyReminderEventHandler.handle(message);

        // Then
        assertThat(supported).isTrue();
        ReminderEvent updated = reminderEventRepository.findById(seed.event().getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReminderEventStatus.SNOOZED);
        assertThat(updated.getSnoozeCount()).isEqualTo(1);
        assertThat(updated.getSnoozedUntil()).isNotNull();
        assertThat(notificationCaptureStore.getSentNotifications()).hasSize(1);
        assertThat(notificationCaptureStore.getSentNotifications().getFirst().payload())
                .contains("adiado");
    }

    @Test
    @DisplayName("Given a pending reminder event and Pular button response, " +
            "when handler processes message, " +
            "then event is marked as SKIPPED and confirmation is sent")
    void givenPendingEventAndSkippedButtonWhenHandleThenMarksSkippedAndSendsConfirmation() {
        // Given
        var seed = scripts.insertPendingReminderEventScript("reply-msg-3");
        AIMessage message = createReplyMessage(seed.user().getWhatsappId(), "reply-msg-3", "pular_btn", "Pular");

        // When
        boolean supported = replyReminderEventHandler.supports(message);
        replyReminderEventHandler.handle(message);

        // Then
        assertThat(supported).isTrue();
        ReminderEvent updated = reminderEventRepository.findById(seed.event().getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReminderEventStatus.SKIPPED);
        assertThat(updated.getResponseReceivedAt()).isNotNull();
        assertThat(notificationCaptureStore.getSentNotifications()).hasSize(2);
        assertThat(notificationCaptureStore.getSentNotifications().getFirst().payload())
                .contains("tomou o medicamento");
    }

    @Test
    @DisplayName("Given a pending reminder event and unknown button response, " +
            "when handler processes message, " +
            "then it keeps event pending and sends guidance notification")
    void givenPendingEventAndUnknownResponseWhenHandleThenKeepsPendingAndSendsGuidance() {
        // Given
        var seed = scripts.insertPendingReminderEventScript("reply-msg-4");
        AIMessage message = createReplyMessage(seed.user().getWhatsappId(), "reply-msg-4", "talvez_btn", "Talvez");

        // When
        boolean supported = replyReminderEventHandler.supports(message);
        replyReminderEventHandler.handle(message);

        // Then
        assertThat(supported).isTrue();
        ReminderEvent updated = reminderEventRepository.findById(seed.event().getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReminderEventStatus.PENDING);
        assertThat(updated.getResponseReceivedAt()).isNull();
        assertThat(notificationCaptureStore.getSentNotifications()).hasSize(1);
        assertThat(notificationCaptureStore.getSentNotifications().getFirst().payload())
                .contains("Não entendi sua resposta")
                .contains("Tomei")
                .contains("Adiar")
                .contains("Pular");
    }

    private AIMessage createReplyMessage(String whatsappId, String replyToId, String buttonId, String body) {
        AIMessage message = new AIMessage();
        message.setId("msg-" + buttonId);
        message.setWhatsappId(whatsappId);
        message.setMessageType("interactive");
        message.setReplyToId(replyToId);
        message.setInteractiveButtonId(buttonId);
        message.setBody(body);
        return message;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        MessageClassifier messageClassifier() {
            return message -> {
                AiMessageProcessorDto dto = new AiMessageProcessorDto();
                switch (message.getInteractiveButtonId()) {
                    case "tomei_btn" -> dto.setType(MessageReceivedType.REMINDER_RESPONSE_TAKEN);
                    case "adiar_btn" -> dto.setType(MessageReceivedType.REMINDER_RESPONSE_SNOOZED);
                    case "pular_btn" -> dto.setType(MessageReceivedType.REMINDER_RESPONSE_SKIPPED);
                    default -> dto.setType(MessageReceivedType.SUPPORT);
                }
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
            return notificationCaptureStore::capture;
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
