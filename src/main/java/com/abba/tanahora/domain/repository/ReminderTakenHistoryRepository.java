package com.abba.tanahora.domain.repository;

import com.abba.tanahora.domain.model.ReminderTakenHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface ReminderTakenHistoryRepository extends MongoRepository<ReminderTakenHistory, UUID> {

    List<ReminderTakenHistory> findAllByUserId(String userId);
    boolean existsByUserIdAndPatientNameIsNotNull(String userId);
}
