package com.abba.tanahora.domain.repository;

import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.ReminderEvent;
import com.abba.tanahora.domain.model.ReminderEventStatus;
import com.abba.tanahora.domain.model.User;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderEventRepository extends MongoRepository<ReminderEvent, UUID> {

    Optional<ReminderEvent> findFirstByWhatsappMessageId(String whatsappMessageId);
    Optional<ReminderEvent> findLastByUserWhatsappIdAndStatus(String whatsappId, ReminderEventStatus status);
    Optional<ReminderEvent> findFirstByReminderAndStatusOrderBySentAtDesc(Reminder reminder, ReminderEventStatus status);
    List<ReminderEvent> findAllByReminderUserId(String userId);
}
