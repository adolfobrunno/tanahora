package com.abba.tanahora.infrastructure.scheduler;

import com.abba.tanahora.application.notification.WhatsAppMessage;
import com.abba.tanahora.domain.model.Reminder;
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

@SpringBootTest(
        properties = {
                "spring.task.scheduling.enabled=false",
                "tictacmed.scheduler.enabled=true"
        }
)
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(MongoCollectionsCleanupExtension.class)
class ReminderSenderJobIntegrationTest {

    @Container
    static final MongoDBContainer MONGO_DB_CONTAINER = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void registerMongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_DB_CONTAINER::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> "tanahora-tests");
    }

    @Autowired
    private ReminderSenderJob reminderSenderJob;

    @Autowired
    private ReminderSenderJobDbScripts scripts;

    @Autowired
    private ReminderEventRepository reminderEventRepository;

    @Autowired
    private NotificationCaptureStore notificationCaptureStore;

    @BeforeEach
    void cleanNotificationStore() {
        notificationCaptureStore.clear();
    }

    @Test
    @DisplayName("Given active reminder without pending event, " +
            "when job runs, " +
            "then it sends a notification and registers a pending event")
    void givenActiveReminderWithoutPendingEventWhenJobRunsThenSendsAndRegistersEvent() {
        // Given
        scripts.insertReminderReadyToSendScript();

        // When
        reminderSenderJob.sendRemindNotification();

        // Then
        assertThat(notificationCaptureStore.getSentNotifications()).hasSize(1);
        List<ReminderEvent> events = reminderEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getStatus()).isEqualTo(ReminderEventStatus.PENDING);
        assertThat(events.getFirst().getWhatsappMessageId()).isEqualTo("msg-1");
    }

    @Test
    @DisplayName("Given pending reminder event overdue by more than 15 minutes, " +
            "when job runs, " +
            "then it marks event as MISSED and does not send new notification")
    void givenPendingOverdueEventWhenJobRunsThenMarksAsMissedWithoutSending() {
        // Given
        ReminderSenderJobDbScripts.SeedWithPendingEvent seed = scripts.insertReminderWithPendingOverdueEventScript();

        // When
        reminderSenderJob.sendRemindNotification();

        // Then
        assertThat(notificationCaptureStore.getSentNotifications()).isEmpty();
        ReminderEvent updated = reminderEventRepository.findById(seed.pendingEvent().getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReminderEventStatus.MISSED);
    }

    @Test
    @DisplayName("Given reminder with a previous MISSED event, " +
            "when job runs, " +
            "then it sends a new reminder and keeps MISSED history")
    void givenReminderWithMissedEventWhenJobRunsThenSendsNewReminderAndKeepsHistory() {
        // Given
        Reminder reminder = scripts.insertReminderWithPreviousMissedEventScript();

        // When
        reminderSenderJob.sendRemindNotification();

        // Then
        assertThat(notificationCaptureStore.getSentNotifications()).hasSize(1);
        List<ReminderEvent> events = reminderEventRepository.findAll();
        assertThat(events).hasSize(2);
        assertThat(events)
                .extracting(ReminderEvent::getStatus)
                .containsExactlyInAnyOrder(ReminderEventStatus.MISSED, ReminderEventStatus.PENDING);
        assertThat(events)
                .filteredOn(event -> event.getStatus() == ReminderEventStatus.PENDING)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getReminder().getId()).isEqualTo(reminder.getId());
                    assertThat(event.getWhatsappMessageId()).isEqualTo("msg-1");
                });
    }

    @TestConfiguration
    static class NotificationTestConfig {
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
